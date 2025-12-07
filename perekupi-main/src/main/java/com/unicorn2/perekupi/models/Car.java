package com.unicorn2.perekupi.models;

import jakarta.persistence.*;

@Entity
@Table(name="cars")
public class Car {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int uid;
    private String company; 
    private String carId;
    private String pictureUrl;
    private String descriptionDisplayName;
    private String descriptionUrl;
    private String model;
    private String year;
    private String volume;
    private int mileage;
    private double price;
    private boolean isNew;
    private double mark;

    public Car() {
    }

    public Car(int uid, String company, String carId, String pictureUrl, String descriptionDisplayName,
            String descriptionUrl, String model, String year, String volume, int mileage, double price, boolean isNew, double mark) {
        this.uid = uid;
        this.company = company;
        this.carId = carId;
        this.pictureUrl = pictureUrl;
        this.descriptionDisplayName = descriptionDisplayName;
        this.descriptionUrl = descriptionUrl;
        this.model = model;
        this.year = year;
        this.volume = volume;
        this.mileage = mileage;
        this.price = price;
        this.isNew = isNew;
        this.mark = mark;
    }

    public int getUid() {
        return uid;
    }
    public void setUid(int uid) {
        this.uid = uid;
    }
    public String getCompany() {
        return company;
    }
    public void setCompany(String company) {
        this.company = company;
    }
    public String getCarId() {
        return carId;
    }
    public void setCarId(String carId) {
        this.carId = carId;
    }
    public String getPictureUrl() {
        return pictureUrl;
    }
    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }
    public String getDescriptionDisplayName() {
        return descriptionDisplayName;
    }
    public void setDescriptionDisplayName(String descriptionDisplayName) {
        this.descriptionDisplayName = descriptionDisplayName;
    }
    public String getDescriptionUrl() {
        return descriptionUrl;
    }
    public void setDescriptionUrl(String descriptionUrl) {
        this.descriptionUrl = descriptionUrl;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public String getYear() {
        return year;
    }
    public void setYear(String year) {
        this.year = year;
    }
    public String getVolume() {
        return volume;
    }
    public void setVolume(String volume) {
        this.volume = volume;
    }
    public int getMileage() {
        return mileage;
    }
    public void setMileage(int mileage) {
        this.mileage = mileage;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public boolean isNew() {
        return isNew;
    }
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public void setMark(double mark) {
        this.mark = mark;
    }
    public double getMark(){
        return this.mark;
    }

}
