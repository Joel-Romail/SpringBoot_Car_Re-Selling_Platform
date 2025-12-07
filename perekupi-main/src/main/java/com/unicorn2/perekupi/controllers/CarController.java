package com.unicorn2.perekupi.controllers;

import com.unicorn2.perekupi.models.Car;
import com.unicorn2.perekupi.models.CarRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cars")
public class CarController {

    private final CarRepository carRepo;

    public CarController(CarRepository carRepo) {
        this.carRepo = carRepo;
    }

    /** Redirect /cars -> /cars/view */
    @GetMapping
    public String index() {
        return "redirect:/cars/view";
    }

    /** List all cars */
    @GetMapping("/view")
    public String showAll(Model model) {
        model.addAttribute("cars", carRepo.findAll());
        return "cars/showAll"; // => src/main/resources/templates/cars/showAll.html
    }

    /** Add a new car from the form on showAll.html */
    @PostMapping("/add")
    public String addCar(@ModelAttribute Car car, RedirectAttributes ra) {
        // If your Car has primitive int uid, make sure the form doesn't send a non-zero uid.
        // With Integer uid, leaving it null is perfect and JPA will generate it.
        carRepo.save(car);
        ra.addFlashAttribute("msg", "Car saved successfully.");
        return "redirect:/cars/view?saved=1";
    }

    /** Delete a car by primary key (uid) from the per-row Delete button */
    @PostMapping("/{uid}/delete")
    public String deleteCar(@PathVariable Integer uid, RedirectAttributes ra) {
        if (!carRepo.existsById(uid)) {
            ra.addFlashAttribute("error", "Car not found: id=" + uid);
            return "redirect:/cars/view";
        }
        try {
            carRepo.deleteById(uid);
            ra.addFlashAttribute("msg", "Deleted car id: " + uid);
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Cannot delete: car is referenced by other data.");
        }
        return "redirect:/cars/view";
    }
}
