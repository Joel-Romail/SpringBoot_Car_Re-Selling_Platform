package com.unicorn2.perekupi.controllers;

import com.unicorn2.perekupi.services.CarImportService;
import com.unicorn2.perekupi.services.CarImportService.ImportResult;
import com.unicorn2.perekupi.web.dto.CarImportDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cars")
public class CarImportController {

    private final CarImportService importService;

    public CarImportController(CarImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/upload-json")
    public String uploadJson(@RequestParam("file") MultipartFile file,
                             RedirectAttributes ra) {
        try {
            ImportResult result = importService.importJson(file);
            ra.addFlashAttribute("msg",
                "Imported " + result.totalRead() + " item(s): " +
                result.inserted() + " inserted, " + result.updated() + " updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }
        return "redirect:/cars";
    }

    // Chunked/batched import: send a JSON array directly
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> importArray(@RequestBody List<CarImportDto> items) {
        var res = importService.importFromList(items); // see method below
        Map<String, Object> out = new HashMap<>();
        out.put("received", res.totalRead());
        out.put("inserted", res.inserted());
        out.put("updated", res.updated());
        return out;
    }





    
}
