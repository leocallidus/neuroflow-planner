package com.example.neuroflowplanner.service.search;

import com.example.neuroflowplanner.model.search.GlobalSearchResult;

import java.util.List;

public interface GlobalSearchService {
    List<GlobalSearchResult> search(String query, int limit);
}
