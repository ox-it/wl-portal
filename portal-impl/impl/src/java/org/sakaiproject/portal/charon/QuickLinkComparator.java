package org.sakaiproject.portal.charon;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by neelam on 24/07/2015.
 * Comparator to Sort the quick links
 */
public class QuickLinkComparator implements Comparator {

    public int compare(Object object1, Object object2) {
        Map<String, String> quickLink1 = (Map<String, String>) object1;
        Map<String, String> quickLink2 = (Map<String, String>) object2;
        return quickLink1.get("name").compareTo(quickLink2.get("name"));
    }

}
