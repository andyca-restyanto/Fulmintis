// filepath: /backend/src/main/java/com/example/app/modules/dashboard/dto/TimelinePointDTO.java
package com.example.app.modules.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** Satu hari pada execution timeline. Match dgn TimelinePoint (FE); date = "yyyy-MM-dd". */
@Getter
@Builder
public class TimelinePointDTO {
    private LocalDate date;
    private long passed;
    private long failed;
    private long blocked;
}
