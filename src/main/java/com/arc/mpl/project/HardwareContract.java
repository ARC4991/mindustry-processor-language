package com.arc.mpl.project;

import java.util.List;
import java.util.Map;

/** Parsed external hardware declarations. Game aliases remain deployment-time facts. */
public record HardwareContract(
    List<LinkDeclaration> links,
    Map<String, String> messages
) {
    public HardwareContract {
        links = List.copyOf(links);
        messages = Map.copyOf(messages);
    }

    public record LinkDeclaration(String mplName, String mplType, String gameAlias) {
    }

}
