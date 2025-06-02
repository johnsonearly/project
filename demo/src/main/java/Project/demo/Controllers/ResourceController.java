package Project.demo.Controllers;

import Project.demo.DTOs.ResourcesDTO;
import Project.demo.Service.AppUserServiceImplementation;
import Project.demo.Service.ResourcesServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/")
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
public class ResourceController {

    @Autowired
    private ResourcesServiceImpl service;
    @Autowired
    private AppUserServiceImplementation serviceImplementation;

    @GetMapping("getResource/{userName}")
    public List<ResourcesDTO> getResources(@PathVariable String userId){
        String userLevel = serviceImplementation.getUser(userId).getProficiency();
        return service.getExercisesByLevel(userLevel);
    }

}
