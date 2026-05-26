package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class) // тесты независимы
public class MoviesApiTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static Gson gson;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        gson = new Gson();
    }

    @BeforeEach
    void setUp() {
        store.clear(); // каждый тест с чистым хранилищем
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());
        assertContentTypeJsonUtf8(resp);
        List<Movie> movies = parseMovieList(resp.body());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMovies_afterAdding_returnsList() throws Exception {
        addMovie("Inception", 2010);
        addMovie("Interstellar", 2014);

        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());
        List<Movie> movies = parseMovieList(resp.body());
        assertEquals(2, movies.size());
    }

    @Test
    void postMovie_whenValid_returnsCreated() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("title", "Test Movie");
        json.addProperty("year", 2000);

        HttpResponse<String> resp = sendPost("/movies", json.toString());
        assertEquals(201, resp.statusCode());
        assertContentTypeJsonUtf8(resp);
        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(movie);
        assertEquals("Test Movie", movie.getTitle());
        assertEquals(2000, movie.getYear());
        assertTrue(movie.getId() > 0);
    }

    @Test
    void postMovie_whenEmptyTitle_returns422() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("title", "");
        json.addProperty("year", 2020);

        HttpResponse<String> resp = sendPost("/movies", json.toString());
        assertEquals(422, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", err.getError());
        assertTrue(err.getDetails().stream().anyMatch(s -> s.contains("не должно быть пустым")));
    }

    @Test
    void postMovie_whenTitleTooLong_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        JsonObject json = new JsonObject();
        json.addProperty("title", longTitle);
        json.addProperty("year", 2020);

        HttpResponse<String> resp = sendPost("/movies", json.toString());
        assertEquals(422, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(err.getDetails().stream().anyMatch(s -> s.contains("не должно превышать 100")));
    }

    @Test
    void postMovie_whenInvalidYear_returns422() throws Exception {
        int currentYear = Year.now().getValue();
        JsonObject json1 = new JsonObject();
        json1.addProperty("title", "Old");
        json1.addProperty("year", 1887);
        assertEquals(422, sendPost("/movies", json1.toString()).statusCode());

        JsonObject json2 = new JsonObject();
        json2.addProperty("title", "Future");
        json2.addProperty("year", currentYear + 2);
        assertEquals(422, sendPost("/movies", json2.toString()).statusCode());

        String badJson = "{\"title\":\"Test\",\"year\":\"abc\"}";
        assertEquals(422, sendPost("/movies", badJson).statusCode());
    }

    @Test
    void postMovie_whenWrongContentType_returns415() throws Exception {
        String json = "{\"title\":\"Test\",\"year\":2020}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, resp.statusCode());
    }

    @Test
    void postMovie_whenMalformedJson_returns422() throws Exception {
        String badJson = "not a json";
        HttpResponse<String> resp = sendPost("/movies", badJson);
        assertEquals(422, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertNotNull(err.getError());
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie added = addMovie("Avatar", 2009);
        HttpResponse<String> resp = sendGet("/movies/" + added.getId());
        assertEquals(200, resp.statusCode());
        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(added.getId(), movie.getId());
        assertEquals("Avatar", movie.getTitle());
        assertEquals(2009, movie.getYear());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/999");
        assertEquals(404, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", err.getError());
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/abc");
        assertEquals(400, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", err.getError());
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        Movie added = addMovie("Delete Me", 2021);
        HttpResponse<String> resp = sendDelete("/movies/" + added.getId());
        assertEquals(204, resp.statusCode());
        // проверяем, что фильм действительно удалён
        assertEquals(404, sendGet("/movies/" + added.getId()).statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/404");
        assertEquals(404, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", err.getError());
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/xyz");
        assertEquals(400, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", err.getError());
    }

    @Test
    void getMoviesByYear_returnsFilteredList() throws Exception {
        addMovie("Movie 2001", 2001);
        addMovie("Another 2001", 2001);
        addMovie("Movie 2002", 2002);

        HttpResponse<String> resp = sendGet("/movies?year=2001");
        assertEquals(200, resp.statusCode());
        List<Movie> movies = parseMovieList(resp.body());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2001));
    }

    @Test
    void getMoviesByYear_whenNoMatch_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=1999");
        assertEquals(200, resp.statusCode());
        List<Movie> movies = parseMovieList(resp.body());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=abc");
        assertEquals(400, resp.statusCode());
        ErrorResponse err = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", err.getError());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Movie addMovie(String title, int year) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("title", title);
        json.addProperty("year", year);
        HttpResponse<String> resp = sendPost("/movies", json.toString());
        if (resp.statusCode() != 201) {
            throw new RuntimeException("Ошибка при добавлении фильма: " + resp.body());
        }
        return gson.fromJson(resp.body(), Movie.class);
    }

    private List<Movie> parseMovieList(String body) {
        return gson.fromJson(body, new ListOfMoviesTypeToken().getType());
    }

    private void assertContentTypeJsonUtf8(HttpResponse<?> resp) {
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(ct.contains("application/json") && ct.contains("utf-8"),
                "Content-Type должен быть application/json; charset=UTF-8, но получен: " + ct);
    }
}