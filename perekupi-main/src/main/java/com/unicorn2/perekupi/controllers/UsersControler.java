package com.unicorn2.perekupi.controllers;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.unicorn2.perekupi.models.CarRepository;
import com.unicorn2.perekupi.models.User;
import com.unicorn2.perekupi.models.UserRepository;
import com.unicorn2.perekupi.services.SsScraperService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class UsersControler {
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private CarRepository carRepo;
    @Autowired
    private SsScraperService scraper;


    @GetMapping("/users/view")
    public String getAllUsers(Model model){
        System.out.println("Getting all users");
        // get all users from database
        List<User> users = userRepo.findAll();
        // end of DB call

        model.addAttribute("us", users);
        return "users/showAll";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam Map<String, String> newuser, HttpServletResponse response){
        System.out.println("ADD user");
        String newName = newuser.get("name");
        String newPwd = newuser.get("password");
        String newCompany = newuser.get("company");
        userRepo.save(new User(newName,newPwd,newCompany));
        response.setStatus(201);
        return "redirect:/users/view";
    }

    @PostMapping("/users/delete-by-name")
    public String deleteByName(@RequestParam String name, RedirectAttributes ra) {
        int n = userRepo.deleteByName(name); // or deleteByNameIgnoreCase
        ra.addFlashAttribute("msg", "Deleted " + n + " user(s) named '" + name + "'.");
        return "redirect:/users/view";
    }
    @GetMapping("/")
    public RedirectView process(){
        return new RedirectView("login");
    }
    
    @GetMapping("/login")
    public String getLogin(Model model, HttpServletRequest request, HttpSession session){
        User user = (User) session.getAttribute("session_user");
        if (user == null){
            return "users/login";
        }
        else {
            model.addAttribute("user",user);
            try {
                var res = scraper.syncBmw();
                model.addAttribute("syncInfo", "Parsed " + res.parsed() + ", inserted " + res.inserted() + " new.");
            } catch (Exception e) {
                model.addAttribute("syncInfo", "Sync failed: " + e.getMessage());
            }
            model.addAttribute("cars", carRepo.findAll());
            return "perekupi";
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam Map<String,String> formData, Model model, HttpServletRequest request, HttpSession session){
        // processing login
        System.out.println("Started logging in");

        String name = formData.get("name");
        String pwd = formData.get("password");
        List<User> userlist = userRepo.findByNameAndPassword(name, pwd);
        if (userlist.isEmpty()){
            System.out.println("Userlist is empty (name: "+name+", pwd: "+pwd+")");
            System.out.println("User repo size: " + userRepo);
            return "users/login";
        }
        else {
            // success
            User user = userlist.get(0);
            request.getSession().setAttribute("session_user", user);
            model.addAttribute("user", user);

            // 1) Run server-side sync (inserts only NEW cars by carId)
            try {
                var res = scraper.syncBmw(); // scrapes ss.lv and inserts only new
                model.addAttribute("syncInfo",
                        "Parsed " + res.parsed() + ", inserted " + res.inserted() + " new.");
            } catch (Exception e) {
                model.addAttribute("syncInfo", "Sync failed: " + e.getMessage());
            }

            // 2) Load fresh cars from DB for the page
            model.addAttribute("cars", carRepo.findAll());

            // 3) Render the Thymeleaf view
            return "perekupi";

        }
    }

    @GetMapping("/logout")
    public String destroySession(HttpServletRequest request){
        request.getSession().invalidate();
        return "redirect:/login";
    }
}
