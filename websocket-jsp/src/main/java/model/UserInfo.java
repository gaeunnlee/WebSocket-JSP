package model;

import lombok.*;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserInfo {
    private UUID userId;
    private String nickname;
    private long totalWin;
    private long totalLose;
    private long coin;
}
