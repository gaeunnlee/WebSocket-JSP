package model;

import lombok.*;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RoomDto {
    private UUID id;
    private UUID hostUserId;
    private String roomName;
    private int isPublic;     
    private int playType;       
    private int totalUserCnt;
    private int currentUserCnt;
}
