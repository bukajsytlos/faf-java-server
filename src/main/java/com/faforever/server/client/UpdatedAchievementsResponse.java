package com.faforever.server.client;

import com.faforever.server.api.dto.AchievementState;
import com.faforever.server.response.ServerResponse;
import lombok.Data;

import java.util.List;

/**
 * Tells the client that the list of achievements has been updated.
 */
@Data
public class UpdatedAchievementsResponse implements ServerResponse {
  private final List<UpdatedAchievement> updatedAchievements;

  @Data
  public static class UpdatedAchievement {
    private final Integer currentSteps;
    private final AchievementState state;
    private final boolean newlyUnlocked;
  }
}
