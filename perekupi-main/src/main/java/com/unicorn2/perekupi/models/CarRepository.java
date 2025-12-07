package com.unicorn2.perekupi.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarRepository extends JpaRepository<Car,Integer> {
    List<Car> findByCompany(String company);
    List<Car> findByCarId(String carId);
    List<Car> findByCompanyAndModelAndYear(String company, String model, String year);

}
