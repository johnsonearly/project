package Project.demo.Entity;

import Project.demo.Enums.UserRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Generated;

@Entity
@Data
@Table(name = "app_user_info")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String userName;
    private String name;
    private UserRole role;
    private String password;
    private String proficiency ;
    private boolean pretestDone;
    private boolean postTestDone;
    private boolean adaptiveExerciseDone;



}
