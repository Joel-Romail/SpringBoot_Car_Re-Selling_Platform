package com.unicorn2.perekupi.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unicorn2.perekupi.models.Car;
import com.unicorn2.perekupi.models.CarRepository;
import com.unicorn2.perekupi.web.dto.CarImportDto;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CarImportService {

    private final ObjectMapper objectMapper;
    private final CarRepository carRepository;

    public CarImportService(ObjectMapper objectMapper, CarRepository carRepository) {
        this.objectMapper = objectMapper;
        this.carRepository = carRepository;
    }

    @Transactional
    public ImportResult importJson(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!name.endsWith(".json")) {
            throw new IllegalArgumentException("Please upload a .json file");
        }

        List<CarImportDto> dtos;
        try (InputStream in = file.getInputStream()) {
            try {
                dtos = objectMapper.readValue(in, new TypeReference<List<CarImportDto>>() {});
            } catch (Exception ex) {
                try (InputStream in2 = file.getInputStream()) {
                    CarImportDto single = objectMapper.readValue(in2, CarImportDto.class);
                    dtos = new ArrayList<>();
                    dtos.add(single);
                }
            }
        }

        int inserted = 0;
        int updated = 0;

        for (CarImportDto dto : dtos) {
            if (dto == null || dto.carId == null || dto.carId.isBlank()) continue;

            // If there are multiple with same carId, update the first; otherwise create new
            var matches = carRepository.findByCarId(dto.carId);
            Car car = matches != null && !matches.isEmpty() ? matches.get(0) : new Car();

            // apply known fields if setters present in current Car class
            setIfPresent(car, "setCompany", dto.company);
            setIfPresent(car, "setCarId", dto.carId);
            setIfPresent(car, "setModel", dto.model);
            setIfPresent(car, "setPictureUrl", dto.pictureUrl);
            setIfPresent(car, "setDescriptionDisplayName", dto.descriptionDisplayName);
            setIfPresent(car, "setDescriptionUrl", dto.descriptionUrl);
            // Optional numeric fields with multiple possible signatures
            setIfPresent(car, "setYear", dto.year);
            setIfPresent(car, "setVolume", dto.volume);
            setIfPresent(car, "setMileage", dto.mileage);
            setIfPresent(car, "setMark", 0.0);
            if (dto.price != null) {
                // support either (double) or (BigDecimal)
                if (!setIfPresent(car, "setPrice", dto.price)) {
                    setIfPresent(car, "setPrice", dto.price.doubleValue());
                }
            }
            if (dto.isNew != null) {
                // support either setNew(boolean) or setIsNew(Boolean)
                if (!setIfPresent(car, "setNew", dto.isNew)) {
                    setIfPresent(car, "setIsNew", dto.isNew);
                }
            }

            boolean isNewEntity = (matches == null || matches.isEmpty());
            carRepository.save(car);
            if (isNewEntity) inserted++; else updated++;
        }

        return new ImportResult(inserted, updated, dtos.size());
    }
    /* 
    private boolean setIfPresent(Object target, String methodName, Object value) {
        if (value == null) return false;
        Method[] methods = target.getClass().getMethods();
        for (Method m : methods) {
            if (!m.getName().equals(methodName)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) continue;
            try {
                // Simple numeric widening for price as needed
                if (params[0] == double.class && value instanceof BigDecimal bd) {
                    m.invoke(target, bd.doubleValue());
                    return true;
                }
                if (params[0].isPrimitive()) {
                    if (params[0] == int.class && value instanceof Integer i) { m.invoke(target, i); return true; }
                    if (params[0] == long.class && value instanceof Long l) { m.invoke(target, l); return true; }
                    if (params[0] == double.class && value instanceof Double d) { m.invoke(target, d); return true; }
                    if (params[0] == boolean.class && value instanceof Boolean b) { m.invoke(target, b); return true; }
                } else {
                    if (params[0].isInstance(value) || (params[0] == String.class && !(value instanceof String))) {
                        // auto toString for String setter
                        if (params[0] == String.class) {
                            m.invoke(target, String.valueOf(value));
                        } else {
                            m.invoke(target, value);
                        }
                        return true;
                    }
                    // BigDecimal setter but value is Double
                    if (params[0].getName().equals("java.math.BigDecimal") && value instanceof Double d) {
                        m.invoke(target, java.math.BigDecimal.valueOf(d));
                        return true;
                    }
                }
            } catch (Exception ignore) { }
        }
        return false;
    }
    */
    public record ImportResult(int inserted, int updated, int totalRead) {}

    public ImportResult importFromList(List<CarImportDto> dtos) {
        int inserted = 0, updated = 0;
        if (dtos == null) return new ImportResult(0, 0, 0);
        for (CarImportDto dto : dtos) {
            if (dto == null || dto.carId == null || dto.carId.isBlank()) continue;
            var existing = carRepository.findByCarId(dto.carId).stream().findFirst().orElse(null);
            if (existing == null) {
                var c = new Car();
                apply(c, dto);
                // mark first-time inserts as NEW, if you want that behavior here too:
                try { Car.class.getMethod("setNew", boolean.class); c.setNew(true); } catch (Exception ignore) {}
                carRepository.save(c);
                inserted++;
            } else {
                apply(existing, dto);
                carRepository.save(existing);
                updated++;
            }
        }
        return new ImportResult(inserted, updated, dtos.size());
    }

    // Map a CarImportDto onto a Car entity (null-safe, only sets non-null dto fields).
    private void apply(Car c, CarImportDto d) {
        if (d == null) return;

        // Strings
        setIfPresent(c, "setCompany", d.company);
        setIfPresent(c, "setCarId", d.carId);
        setIfPresent(c, "setModel", d.model);
        setIfPresent(c, "setVolume", d.volume);
        setIfPresent(c, "setDescriptionDisplayName", d.descriptionDisplayName);
        setIfPresent(c, "setDescriptionUrl", d.descriptionUrl);
        setIfPresent(c, "setPictureUrl", d.pictureUrl);

        // Year is a String in your current design; if your entity still has Integer year,
        // this will simply skip if the setter type doesn't match.
        setIfPresent(c, "setYear", d.year); // expects String; will be skipped if entity expects Integer

        // Mileage: if your entity uses int/Integer mileage.
        setIfPresent(c, "setMileage", d.mileage); // supports Long/Integer -> int

        // Price: support either double or BigDecimal in the entity
        if (d.price != null) {
            // Try BigDecimal first, then double
            if (!setIfPresent(c, "setPrice", d.price)) {
                setIfPresent(c, "setPrice", d.price.doubleValue());
            }
        }

        // isNew flag (Boolean vs primitive boolean)
        if (d.isNew != null) {
            if (!setIfPresent(c, "setNew", d.isNew)) {
                setIfPresent(c, "setIsNew", d.isNew);
            }
        }
    }

    /**
     * Try to call a setter if it exists with a compatible parameter type.
     * Returns true if a call was made, false if no compatible setter was found or value was null.
     */
    private boolean setIfPresent(Object target, String methodName, Object value) {
        if (target == null || value == null) return false;
        try {
            for (java.lang.reflect.Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1) continue;

                Object arg = value;

                // Allow common numeric conversions
                if (p[0].isPrimitive()) {
                    if (p[0] == int.class && value instanceof Number n) { m.invoke(target, n.intValue()); return true; }
                    if (p[0] == long.class && value instanceof Number n) { m.invoke(target, n.longValue()); return true; }
                    if (p[0] == double.class && value instanceof Number n) { m.invoke(target, n.doubleValue()); return true; }
                    if (p[0] == boolean.class && value instanceof Boolean b) { m.invoke(target, b.booleanValue()); return true; }
                } else {
                    // BigDecimal setter while value is Double
                    if (p[0].getName().equals("java.math.BigDecimal") && value instanceof Double d) {
                        m.invoke(target, java.math.BigDecimal.valueOf(d));
                        return true;
                    }
                    // String setter → toString for non-Strings
                    if (p[0] == String.class && !(value instanceof String)) {
                        m.invoke(target, String.valueOf(value));
                        return true;
                    }
                    // Wrapper types / exact match
                    if (p[0].isInstance(value)) {
                        m.invoke(target, arg);
                        return true;
                    }
                    // Allow Integer target from Long dto etc.
                    if (Number.class.isAssignableFrom(p[0]) && value instanceof Number n) {
                        if (p[0] == Integer.class) { m.invoke(target, Integer.valueOf(n.intValue())); return true; }
                        if (p[0] == Long.class)    { m.invoke(target, Long.valueOf(n.longValue())); return true; }
                        if (p[0] == Double.class)  { m.invoke(target, Double.valueOf(n.doubleValue())); return true; }
                    }
                }
            }
        } catch (Exception ignore) { /* no-op if incompatible */ }
        return false;
    }





}
