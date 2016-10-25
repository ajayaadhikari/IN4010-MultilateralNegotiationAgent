package ai2016.group5;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.Deadline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;

import negotiator.boaframework.SortedOutcomeSpace;
import negotiator.actions.DefaultAction;

import negotiator.parties.AbstractNegotiationParty;
import negotiator.session.TimeLineInfo;
import negotiator.utility.AbstractUtilitySpace;

/**
 * The negotiation party.
 */
public class Group5 extends AbstractNegotiationParty {
	// Latest bid received by the opponents
	private Bid lastReceivedBid = null;

	// Map of the opponents
	private HashMap<AgentID, Opponent> opponentsMap;
	// Percentage of time in which we'll just keep offering the maximum utility bid
	private double TIME_OFFERING_MAX_UTILITY_BID = 0.20D;
	// Utility above which all of our offers will be
	private double RESERVATION_VALUE = 0.4D;
		
	private SortedOutcomeSpace SOS;
	private Random randomGenerator;
	
	int turn;


	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl, TimeLineInfo tl, long randomSeed, AgentID agentId) {
		super.init(utilSpace, dl, tl, randomSeed, agentId);
		this.opponentsMap = new HashMap<AgentID, Opponent>();
		this.SOS = new SortedOutcomeSpace(this.utilitySpace);
		this.randomGenerator = new Random();

	}

	/**
	 * Each round this method gets called and ask you to accept or offer. The
	 * first party in the first round is a bit different, it can only propose an
	 * offer.
	 *
	 * @param validActions Either a list containing both accept and offer or only offer.
	 * @return The chosen action.
	 */
	@Override
	public Action chooseAction(List<Class<? extends Action>> validActions) {
		this.turn ++;

		// For the first part of the negotiation, just keep offering the maximum
		// utility bid
		if (isMaxUtilityOfferTime()) {
			Bid maxUtilityBid = null;
			try {
				maxUtilityBid = this.utilitySpace.getMaxUtilityBid();
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Cannot generate max utility bid");
			}
			System.out.println("It's max utility bid time!");
			return new Offer(getPartyId(), maxUtilityBid);
		}
		
		System.out.format("Last received bid had utility of [%f] for me%n", getUtility(this.lastReceivedBid));

		// Generate a acceptable bid
		Bid proposedBid = generateBid();

		// Check if we should accept the latest offer, given the bid we're
		// proposing
		if (isAcceptable(proposedBid)) {
			System.out.println("I'm going to accept the latest offer!");
			return new Accept(getPartyId(), this.lastReceivedBid);
		}

		// Offer the proposed bid
		System.out.format("I'm going to offer the bid that I generated, which has utility [%f]%n", getUtility(proposedBid));
		return new Offer(getPartyId(), proposedBid);
	}
	
	private double getMinAcceptableUtility()
	{
		double timeLeft = 1 - getTimeLine().getTime();
		// At the beginning of the negotiation the minimum utility will be 0.9,
		// when 90% of the time has elapsed it will be ~0.83
		// when 99% of the time has elapsed it will be ~0.77
		// and then it will drop to 0 very rapidly (it will be exactly 0 when no time is left)
		double minUtility = Math.log10(timeLeft) / this.getConcedingFactor() + 0.9D;
		
		return Math.max(minUtility, this.RESERVATION_VALUE);
	}
	
	private double getConcedingFactor()
	{		
		Double max = 0.0;
		Double current = 0.0;
		for (AgentID id : this.opponentsMap.keySet()){
			// Get how hard headed this agent is considering last 40 rounds
			current = this.opponentsMap.get(id).hardHeaded(40);
			if (current != null && current > max){
				max = current;
			}
		}
		// Check whether at least one agent is hard headed
		// If so we should concede faster
		if (max > 0.1){
			return 7.0;
		}
		return 10.0;
	}
	/**
	 * Determines whether we should accept the latest opponent's bid
	 * @param proposedBid The bid that we're going to offer
	 * @return boolean indicating whether we should accept the latest bid
	 */
	private boolean isAcceptable(Bid proposedBid) {
		// Check if the utility of the latest received offer is higher than the utility
		// of the bid we are going to offer
		boolean aNext = getUtility(this.lastReceivedBid) >= getUtility(proposedBid);

		double minUtility = this.getMinAcceptableUtility();
		
		System.out.format("Min utility: [%f]%n", minUtility);
		
		// We accept the latest offer if it has a greater utility than the one we are proposing,
		// or if its utility is higher than our minUtility
		return (aNext || getUtility(this.lastReceivedBid) > minUtility);
	}
	
	/**
	 * Determines if we're in the time in which we should just keep
	 * offering the max utility bid
	 * @return boolean indicating whether we should offer the maximum utility bid
	 */
	private boolean isMaxUtilityOfferTime() {
		return getTimeLine().getTime() < this.TIME_OFFERING_MAX_UTILITY_BID;
	}

	/**
	 * Reception of offers made by other parties.
	 * @param sender The party that did the action. Can be null.
	 * @param action The action that party did.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);

		// If we're receiving an offer
		if (sender != null && action instanceof Offer) {
			// Store the bid as the latest received bid
			this.lastReceivedBid = ((Offer) action).getBid();

			// Store the bid in the opponent's history
			if (opponentsMap.containsKey(sender)) {
				opponentsMap.get(sender).addBid(this.lastReceivedBid);
			} else {
				// If it's the first time we see this opponent, create a new
				// entry in the opponent map
				try {
					Opponent newOpponent = new Opponent(generateRandomBid());
					newOpponent.addBid(this.lastReceivedBid);
					opponentsMap.put(sender, newOpponent);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Generates a random bid that has an higher utility than
	 * our own reservation value, by only searching in the space of acceptable utility
	 * @return the random bid with utility higher than the reservation value
	 */
	private Bid generateAcceptableRandomBid(double minimum_acceptable_utility) {
		Bid bid;		
		do {
			// Generate random double in range 0:1
		    double randomNumber = this.randomGenerator.nextDouble();
		    // Map randomNumber in range this.RESERVATION_VALUE : 1
		    double utility = minimum_acceptable_utility + randomNumber * (1.0 - minimum_acceptable_utility);
		    // Get a bid closest to $utility
		    bid = SOS.getBidNearUtility(utility).getBid();
		} while (getUtility(bid) < minimum_acceptable_utility);
		return bid;
	}
	/**
	 * Generates a new bid basing on our own utility and the estimated utility
	 * of the opponents, through frequency analysis
	 * @return the generated bid, which has always a utility higher than our reservation value
	 */
	private Bid generateBid() {
		double opponentsTotalUtility;
		double averageOpponentsUtility;
		Bid randomBid;
		
		double acceptableUtility = this.getMinAcceptableUtility();
		Bid bestBid = generateAcceptableRandomBid(acceptableUtility);
		double bestOverallUtility = 0;

		// Generate 100 times random (valid) bids and see which one has a better overall utility
		for (int i = 0; i < 100; i++) {
			// Generate a valid random bid
			randomBid = generateAcceptableRandomBid(acceptableUtility);

			opponentsTotalUtility = 0;
			for (AgentID agent : this.opponentsMap.keySet()) {
				opponentsTotalUtility += this.opponentsMap.get(agent).getUtility(randomBid);
			}

			// Get the average utility for the opponents
			averageOpponentsUtility = opponentsTotalUtility / this.opponentsMap.size();

			if (averageOpponentsUtility > bestOverallUtility) {
				bestBid = randomBid;
			}
		}
		return bestBid;
	}

	/**
	 * Description of the agent
	 */
	@Override
	public String getDescription() {
		return "Party Group 5";
	}

}
