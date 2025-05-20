package Project.demo.Controllers;


import Project.demo.DTOs.LoginDTO;
import Project.demo.Entity.AppUser;
import Project.demo.Responses.SuccessMessage;
import Project.demo.Service.AppUserServiceImplementation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/app-users/")
@CrossOrigin(origins = "http://localhost:5173")
public class AppUserController {
    @Autowired
    public AppUserServiceImplementation userServiceImplementation;

    @GetMapping("/getAllUsers")
    public SuccessMessage<List<AppUser>> getAllUsers(){
        SuccessMessage<List<AppUser>> message = new SuccessMessage<>();
        message.setStatusCode("201");
        message.setSuccessMessage("Users gotten successfully");
        message.setData(userServiceImplementation.getAllUsers());
        return message;
    }

    @GetMapping("get-user-{user_id}")
    public SuccessMessage<Optional<AppUser>> getUserById(@PathVariable int id){
        SuccessMessage<Optional<AppUser>> message = new SuccessMessage<>();
        message.setStatusCode("201");
        message.setSuccessMessage("User" + id + " gotten successfully");
        message.setData(userServiceImplementation.getAppUserById(id));
        return message;
    }

    @PostMapping("create-user")
    public SuccessMessage<AppUser> createUser(@RequestBody AppUser appUser){
        SuccessMessage<AppUser> message  = new SuccessMessage<>();
        message.setStatusCode("201");
        message.setSuccessMessage("User created successfully");
        message.setData(userServiceImplementation.createUser(appUser));
        return  message;
    }

    @PutMapping("update/{userId}")
    public void updateUser(@PathVariable  String userId){
      userServiceImplementation.updateUser(userId);
    }

    @PostMapping("login")
    public ResponseEntity<SuccessMessage<String>> loginUser(@RequestBody LoginDTO loginDTO) {
        ResponseEntity<String> loginResponse = userServiceImplementation.logUserIn(loginDTO);

        if (loginResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        SuccessMessage<String> message = new SuccessMessage<>();
        message.setStatusCode("200");
        message.setSuccessMessage("User found successfully");
        message.setData(loginResponse.getBody());

        return ResponseEntity.ok(message);
    }




}
