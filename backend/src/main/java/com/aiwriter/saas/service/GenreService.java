package com.aiwriter.saas.service;

import com.aiwriter.entity.StoryGenre;
import java.util.*;

public interface GenreService {
  List<StoryGenre> list(boolean admin);

  StoryGenre save(Long id, Map<String, Object> body);

  String selectedName(long id);
}
