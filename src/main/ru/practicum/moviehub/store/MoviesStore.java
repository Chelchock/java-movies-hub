package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public Movie add(Movie movie) {
        int id = idGenerator.getAndIncrement();
        Movie newMovie = new Movie(id, movie.getTitle(), movie.getYear());
        movies.put(id, newMovie);
        return newMovie;
    }

    public List<Movie> getAll() {
        return List.copyOf(movies.values());
    }

    public Optional<Movie> findById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public boolean deleteById(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> findByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
        idGenerator.set(1);
    }
}