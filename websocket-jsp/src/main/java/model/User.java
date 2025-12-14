package model;

import lombok.*;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    private UUID id;
    private String email;
    private String pwd;        // DB에는 해시 저장 추천
    private Integer loginType; // 0/1/NULL
}
