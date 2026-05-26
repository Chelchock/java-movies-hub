package ru.practicum.moviehub.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();

        try {
            switch (method) {
                case "GET":
                    handleGet(ex, path);
                    break;
                case "POST":
                    handlePost(ex);
                    break;
                case "DELETE":
                    handleDelete(ex, path);
                    break;
                default:
                    sendError(ex, 405, "Метод не поддерживается");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGet(HttpExchange ex, String path) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query != null && query.contains("year=")) {
            // фильтрация по году
            String yearParam = query.split("year=")[1];
            if (yearParam.contains("&")) {
                yearParam = yearParam.substring(0, yearParam.indexOf("&"));
            }
            try {
                int year = Integer.parseInt(yearParam);
                List<Movie> filtered = store.findByYear(year);
                sendJson(ex, 200, gson.toJson(filtered));
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный параметр запроса — 'year'");
            }
            return;
        }

        String[] segments = path.split("/");
        if (segments.length == 3 && !segments[2].isEmpty()) {
            try {
                int id = Integer.parseInt(segments[2]);
                Optional<Movie> movie = store.findById(id);
                if (movie.isPresent()) {
                    sendJson(ex, 200, gson.toJson(movie.get()));
                } else {
                    sendError(ex, 404, "Фильм не найден");
                }
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный ID");
            }
            return;
        }

        List<Movie> all = store.getAll();
        sendJson(ex, 200, gson.toJson(all));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            sendError(ex, 415, "Неподдерживаемый тип контента");
            return;
        }

        JsonObject json;
        try {
            InputStreamReader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            sendError(ex, 422, "Ошибка валидации", List.of("Некорректный JSON"));
            return;
        }

        List<String> errors = new ArrayList<>();
        String title = null;
        if (json.has("title")) {
            title = json.get("title").getAsString();
            if (title.isBlank()) {
                errors.add("название не должно быть пустым");
            } else if (title.length() > 100) {
                errors.add("название не должно превышать 100 символов");
            }
        } else {
            errors.add("поле title обязательно");
        }

        Integer year = null;
        if (json.has("year")) {
            try {
                year = json.get("year").getAsInt();
                int currentYear = Year.now().getValue();
                if (year < 1888 || year > currentYear + 1) {
                    errors.add("год должен быть между 1888 и " + (currentYear + 1));
                }
            } catch (Exception e) {
                errors.add("год должен быть числом");
            }
        } else {
            errors.add("поле year обязательно");
        }

        if (!errors.isEmpty()) {
            sendError(ex, 422, "Ошибка валидации", errors);
            return;
        }

        Movie movie = new Movie(0, title, year);
        Movie created = store.add(movie);
        sendJson(ex, 201, gson.toJson(created));
    }

    private void handleDelete(HttpExchange ex, String path) throws IOException {
        String[] segments = path.split("/");
        if (segments.length != 3 || segments[2].isEmpty()) {
            sendError(ex, 400, "Некорректный путь");
            return;
        }
        try {
            int id = Integer.parseInt(segments[2]);
            boolean deleted = store.deleteById(id);
            if (deleted) {
                sendNoContent(ex);
            } else {
                sendError(ex, 404, "Фильм не найден");
            }
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
        }
    }
}