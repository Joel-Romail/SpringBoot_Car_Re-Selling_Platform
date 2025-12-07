package com.unicorn2.perekupi.controllers;

import com.unicorn2.perekupi.models.Car;
import com.unicorn2.perekupi.models.CarRepository;
import com.unicorn2.perekupi.services.SsScraperService;
import com.unicorn2.perekupi.services.SsScraperService.SyncResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/perekupi")
public class PerekupiPageController {

    private final CarRepository carRepository;
    private final SsScraperService scraper;

    public PerekupiPageController(CarRepository carRepository, SsScraperService scraper) {
        this.carRepository = carRepository;
        this.scraper = scraper;
    }

    /** Opens the perekupi page, first syncing BMW from ss.lv and inserting only new items by carId. */
    @GetMapping
    public String open(Model model) {
        try {
            SyncResult res = scraper.syncBmw();
            model.addAttribute("syncInfo",
                    String.format("Parsed %d, inserted %d new.", res.parsed(), res.inserted()));
        } catch (IOException e) {
            model.addAttribute("syncInfo", "Sync failed: " + e.getMessage());
        }
        List<Car> cars = carRepository.findByCompany("BMW");
        model.addAttribute("cars", cars);
        return "perekupi"; // templates/perekupi.html
    }
}
