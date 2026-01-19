package sentences.service;

import java.util.HashMap;
import java.util.Map;

public class TopicScenariosProvider {

    private static final Map<String, String> TOPIC_DESCRIPTIONS = new HashMap<>();
    private static final Map<String, String> TOPIC_SCENARIOS = new HashMap<>();

    static {
        // 주제 설명
        TOPIC_DESCRIPTIONS.put("restaurant", "Restaurant ordering and dining situations");
        TOPIC_DESCRIPTIONS.put("airport", "Airport check-in, security, and boarding situations");
        TOPIC_DESCRIPTIONS.put("shopping", "Shopping, pricing, and returns situations");
        TOPIC_DESCRIPTIONS.put("hotel", "Hotel check-in, room service, and facilities situations");
        TOPIC_DESCRIPTIONS.put("hospital", "Medical consultation and pharmacy situations");
        TOPIC_DESCRIPTIONS.put("interview", "Job interview and professional situations");
        TOPIC_DESCRIPTIONS.put("daily", "Daily life conversations");
        TOPIC_DESCRIPTIONS.put("directions", "Asking for and giving directions");

        // 주제별 시나리오
        TOPIC_SCENARIOS.put("restaurant",
            "- Asking about ingredients or allergens\n" +
            "- Requesting modifications to dishes\n" +
            "- Inquiring about wait times\n" +
            "- Asking for recommendations\n" +
            "- Handling billing issues (splitting, adding items)\n" +
            "- Requesting doggy bags or takeout\n" +
            "- Complaining politely about food/service\n" +
            "- Asking about portion sizes\n" +
            "- Making special occasion requests\n" +
            "- Inquiring about dietary options (vegan, halal)"
        );

        TOPIC_SCENARIOS.put("airport",
            "- Requesting seat changes or upgrades\n" +
            "- Asking about baggage allowances and fees\n" +
            "- Inquiring about flight delays or gate changes\n" +
            "- Explaining items in luggage to security\n" +
            "- Asking for directions to lounges or gates\n" +
            "- Handling lost baggage situations\n" +
            "- Requesting wheelchair or special assistance\n" +
            "- Asking about connecting flights\n" +
            "- Inquiring about duty-free purchases\n" +
            "- Dealing with visa/immigration questions"
        );

        TOPIC_SCENARIOS.put("shopping",
            "- Asking about prices and discounts\n" +
            "- Requesting different sizes or colors\n" +
            "- Inquiring about return and exchange policies\n" +
            "- Asking for gift wrapping\n" +
            "- Comparing products and features\n" +
            "- Asking about warranties and guarantees\n" +
            "- Inquiring about payment methods\n" +
            "- Requesting store credit or refunds\n" +
            "- Asking about delivery or shipping\n" +
            "- Looking for specific brands or items"
        );

        TOPIC_SCENARIOS.put("hotel",
            "- Confirming reservations and check-in times\n" +
            "- Requesting room upgrades or changes\n" +
            "- Asking about hotel facilities and services\n" +
            "- Ordering room service\n" +
            "- Reporting room issues (AC, TV, cleanliness)\n" +
            "- Inquiring about checkout times and late checkout\n" +
            "- Asking for wake-up calls\n" +
            "- Requesting extra towels or amenities\n" +
            "- Inquiring about local attractions\n" +
            "- Handling billing questions"
        );

        TOPIC_SCENARIOS.put("hospital",
            "- Describing symptoms with specific details\n" +
            "- Asking about medication side effects\n" +
            "- Inquiring about treatment options\n" +
            "- Requesting medical records or documentation\n" +
            "- Asking about insurance coverage\n" +
            "- Scheduling follow-up appointments\n" +
            "- Describing pain levels and locations\n" +
            "- Asking about recovery time\n" +
            "- Inquiring about prescription refills\n" +
            "- Explaining medical history"
        );

        TOPIC_SCENARIOS.put("interview",
            "- Introducing yourself professionally\n" +
            "- Explaining your work experience\n" +
            "- Describing your strengths and weaknesses\n" +
            "- Asking about company culture and values\n" +
            "- Discussing salary expectations\n" +
            "- Asking about growth opportunities\n" +
            "- Explaining career gaps or changes\n" +
            "- Asking about next steps in the process\n" +
            "- Discussing work-life balance\n" +
            "- Expressing interest in the position"
        );

        TOPIC_SCENARIOS.put("daily",
            "- Talking about hobbies and interests\n" +
            "- Discussing weekend plans\n" +
            "- Sharing opinions about movies or shows\n" +
            "- Talking about food preferences\n" +
            "- Discussing travel experiences\n" +
            "- Sharing daily routines\n" +
            "- Talking about goals and aspirations\n" +
            "- Discussing pets or family\n" +
            "- Sharing recommendations\n" +
            "- Making small talk about current events"
        );

        TOPIC_SCENARIOS.put("directions",
            "- Asking for the nearest subway station\n" +
            "- Inquiring about walking distance\n" +
            "- Asking about public transportation options\n" +
            "- Requesting landmark-based directions\n" +
            "- Asking about taxi or ride-share availability\n" +
            "- Inquiring about traffic conditions\n" +
            "- Asking for specific addresses\n" +
            "- Requesting directions to tourist attractions\n" +
            "- Asking about parking availability\n" +
            "- Inquiring about estimated travel time"
        );
    }

    public static String getTopicDescription(String topic) {
        return TOPIC_DESCRIPTIONS.getOrDefault(topic, "General English conversation");
    }

    public static String getTopicScenarios(String topic) {
        return TOPIC_SCENARIOS.getOrDefault(topic, "General scenarios");
    }
}
