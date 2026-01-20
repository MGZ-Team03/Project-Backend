package sentences.service;

import java.util.*;

public class SituationGenerator {

    private static final Map<String, List<SituationTemplate>> SITUATIONS = new HashMap<>();
    private static final Random random = new Random();

    static {
        // Restaurant situations
        SITUATIONS.put("restaurant", Arrays.asList(
            new SituationTemplate(
                "You're at a busy Italian restaurant on a Friday evening. The waiter approaches your table, but they seem to have made a mistake with your reservation - they have you down for 2 people, but you're actually a party of 4.",
                "Waiter at an Italian restaurant",
                "Resolve the reservation issue and get seated with your full party"
            ),
            new SituationTemplate(
                "You've just received your food at a nice restaurant, but you notice something wrong with your dish - it's not what you ordered. The restaurant is quite busy.",
                "Restaurant server",
                "Politely explain the mistake and get the correct dish"
            ),
            new SituationTemplate(
                "You're dining at a restaurant and find a hair in your food. You need to handle this delicately without causing a scene.",
                "Restaurant manager",
                "Get the issue resolved professionally and continue your meal"
            ),
            new SituationTemplate(
                "The bill has arrived, but there's an unexpected charge you don't recognize. You need to clarify what it's for.",
                "Restaurant server",
                "Understand the charge and resolve any billing errors"
            ),
            new SituationTemplate(
                "You have a severe shellfish allergy and need to make absolutely sure your dish doesn't contain any. The menu isn't entirely clear.",
                "Restaurant waiter",
                "Confirm your meal is safe to eat given your allergy"
            )
        ));

        // Airport situations
        SITUATIONS.put("airport", Arrays.asList(
            new SituationTemplate(
                "You've arrived at the airport check-in counter and the airline representative informs you that your flight is overbooked. They're asking for volunteers to take a later flight.",
                "Airline check-in agent",
                "Decide whether to volunteer and negotiate compensation if you do"
            ),
            new SituationTemplate(
                "You've just gone through security and realized you left your phone at the screening area. You need to retrieve it quickly.",
                "Airport security officer",
                "Get your phone back without missing your flight"
            ),
            new SituationTemplate(
                "Your luggage was flagged at security for suspicious items. The officer needs you to explain what's in your bag.",
                "TSA security officer",
                "Clarify the contents and clear security"
            ),
            new SituationTemplate(
                "You've arrived at your destination but your checked luggage didn't make it. You need to file a report and arrange delivery.",
                "Lost baggage service representative",
                "File your claim and arrange to get your luggage when it arrives"
            ),
            new SituationTemplate(
                "You're at the gate and want to upgrade to business class if possible. The flight is boarding soon.",
                "Gate agent",
                "Inquire about upgrade availability and cost"
            )
        ));

        // Shopping situations
        SITUATIONS.put("shopping", Arrays.asList(
            new SituationTemplate(
                "You bought a sweater last week but it doesn't fit right. You have the receipt but no tags on the item anymore.",
                "Store customer service representative",
                "Exchange or return the item despite the missing tags"
            ),
            new SituationTemplate(
                "You're looking for a specific laptop model that's on sale, but you can't find it on the shelves.",
                "Electronics store employee",
                "Locate the laptop or find out when it will be restocked"
            ),
            new SituationTemplate(
                "You want to buy an expensive watch as a gift, but you're unsure if your friend will like it. You need to ask about the return policy.",
                "Jewelry store salesperson",
                "Understand the return/exchange policy before making the purchase"
            ),
            new SituationTemplate(
                "Your credit card was declined at checkout, but you're sure you have funds. You need to figure out what's wrong.",
                "Store cashier",
                "Complete your purchase using an alternative payment method"
            ),
            new SituationTemplate(
                "You're comparing two similar smartphones and need expert advice on which one suits your needs better.",
                "Mobile phone store specialist",
                "Get detailed information to make an informed decision"
            )
        ));

        // Hotel situations
        SITUATIONS.put("hotel", Arrays.asList(
            new SituationTemplate(
                "You've just checked into your hotel room, but the air conditioning isn't working and it's very hot.",
                "Hotel front desk receptionist",
                "Get your AC fixed or move to a different room"
            ),
            new SituationTemplate(
                "You booked a room with an ocean view, but your room faces a parking lot. You want to switch rooms.",
                "Hotel manager",
                "Get the room type you paid for"
            ),
            new SituationTemplate(
                "You need to check out in 10 minutes but room service hasn't arrived with your breakfast order yet.",
                "Room service coordinator",
                "Get your breakfast quickly or arrange an alternative"
            ),
            new SituationTemplate(
                "The hotel charged you for mini-bar items you didn't consume. You need to dispute these charges.",
                "Hotel billing department",
                "Remove the incorrect charges from your bill"
            ),
            new SituationTemplate(
                "Your flight was delayed and you'll arrive several hours after the standard check-in time. You want to ensure your reservation won't be cancelled.",
                "Hotel receptionist",
                "Confirm your late arrival and keep your reservation"
            )
        ));

        // Hospital situations
        SITUATIONS.put("hospital", Arrays.asList(
            new SituationTemplate(
                "You've been experiencing severe headaches for three days. They're getting worse and you're worried it might be serious.",
                "Emergency room doctor",
                "Get a proper diagnosis and treatment plan"
            ),
            new SituationTemplate(
                "The pharmacy doesn't have your prescribed medication in stock. You need it daily for a chronic condition.",
                "Hospital pharmacist",
                "Find an alternative solution to get your medication"
            ),
            new SituationTemplate(
                "Your insurance company is denying coverage for a procedure your doctor recommended. You need to understand why.",
                "Hospital billing coordinator",
                "Clarify the insurance issue and explore payment options"
            ),
            new SituationTemplate(
                "You're picking up test results and the numbers look concerning. You need the doctor to explain what they mean.",
                "Clinic doctor",
                "Understand your test results and next steps"
            ),
            new SituationTemplate(
                "You're having an allergic reaction to a medication prescribed yesterday. You need immediate guidance.",
                "Nurse hotline",
                "Get instructions on how to handle the reaction safely"
            )
        ));

        // Interview situations
        SITUATIONS.put("interview", Arrays.asList(
            new SituationTemplate(
                "The interviewer asks you about a gap in your resume from two years ago when you took time off for personal reasons.",
                "Hiring manager",
                "Explain the gap honestly while keeping the conversation professional"
            ),
            new SituationTemplate(
                "You're asked to describe a time you failed at something. You need to show growth and learning from the experience.",
                "Senior interviewer",
                "Share a genuine failure and what you learned from it"
            ),
            new SituationTemplate(
                "The interviewer mentions the salary range is lower than what you expected. You need to negotiate or inquire about other benefits.",
                "HR representative",
                "Discuss compensation professionally and see if you can reach a fair agreement"
            ),
            new SituationTemplate(
                "You're being interviewed for a role that requires skills you're still developing. You want to be honest but confident.",
                "Technical lead",
                "Acknowledge your learning curve while highlighting your ability to grow"
            ),
            new SituationTemplate(
                "At the end of the interview, you realize this job might not be the right fit. You want to ask questions that will help you decide.",
                "Department head",
                "Gather enough information to make an informed decision about the role"
            )
        ));

        // Daily conversation situations
        SITUATIONS.put("daily", Arrays.asList(
            new SituationTemplate(
                "You meet a coworker in the elevator who seems upset. They mention they're having a tough week.",
                "Coworker",
                "Show empathy and have a brief supportive conversation"
            ),
            new SituationTemplate(
                "Your friend is trying to decide between two job offers and asks for your honest opinion. Both have pros and cons.",
                "Close friend",
                "Help them think through the decision without imposing your preference"
            ),
            new SituationTemplate(
                "You're at a neighborhood gathering and meet someone who shares your hobby. They're excited to discuss it.",
                "New neighbor",
                "Bond over your shared interest and maybe make plans to meet again"
            ),
            new SituationTemplate(
                "A family member calls to tell you about their recent vacation. They're very enthusiastic and want to share all the details.",
                "Excited family member",
                "Show genuine interest and engage in the conversation"
            ),
            new SituationTemplate(
                "You bump into an old classmate you haven't seen in years. The last time you met, things were a bit awkward.",
                "Former classmate",
                "Navigate the encounter gracefully and catch up"
            )
        ));

        // Directions situations
        SITUATIONS.put("directions", Arrays.asList(
            new SituationTemplate(
                "You're lost in an unfamiliar part of the city and your phone battery just died. You need to get to the central train station.",
                "Local pedestrian",
                "Get clear directions to reach the train station"
            ),
            new SituationTemplate(
                "You're looking for a specific museum but the address you have seems to be wrong. You're standing where it should be, but it's not there.",
                "Store owner nearby",
                "Find out where the museum actually is"
            ),
            new SituationTemplate(
                "You need to catch a bus to the airport, but you don't know which number goes there or where the stop is.",
                "Bus driver",
                "Get information about the right bus and timing"
            ),
            new SituationTemplate(
                "You're supposed to meet someone at a restaurant called 'The Blue Olive' but there are three restaurants with similar names nearby.",
                "Restaurant host",
                "Confirm you're at the correct location"
            ),
            new SituationTemplate(
                "You're in a taxi and suspect the driver is taking a longer route than necessary. You want to clarify without being confrontational.",
                "Taxi driver",
                "Politely inquire about the route and ensure you're not being overcharged"
            )
        ));
    }

    /**
     * 랜덤 상황 생성
     */
    public static SituationTemplate generateRandomSituation(String topic) {
        List<SituationTemplate> topicSituations = SITUATIONS.get(topic);
        if (topicSituations == null || topicSituations.isEmpty()) {
            // 기본 상황 반환
            return new SituationTemplate(
                "You're having a conversation in English.",
                "English speaker",
                "Practice your English conversation skills"
            );
        }

        int index = random.nextInt(topicSituations.size());
        return topicSituations.get(index);
    }

    /**
     * 상황 템플릿 클래스
     */
    public static class SituationTemplate {
        private final String situation;
        private final String role;
        private final String goal;

        public SituationTemplate(String situation, String role, String goal) {
            this.situation = situation;
            this.role = role;
            this.goal = goal;
        }

        public String getSituation() {
            return situation;
        }

        public String getRole() {
            return role;
        }

        public String getGoal() {
            return goal;
        }
    }
}
