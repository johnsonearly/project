package Project.demo.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class SuccessMessage<T>{
    private String successMessage;
    private String statusCode;
    private T data;
}
