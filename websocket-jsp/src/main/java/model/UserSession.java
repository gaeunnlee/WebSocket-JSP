package model;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession implements Serializable {
    private UUID id;
    private String email;
    private String nickname;
}
