package com.caregiver.notes;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
  private final ActivityService activity;

  public ActivityController(ActivityService activity) {
    this.activity = activity;
  }

  @GetMapping
  public List<ActivityItemResponse> recent(
      @RequestParam(defaultValue = "10") int limit) {
    return activity.recent(limit);
  }
}
