package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.EnumMap;
import java.util.Map;

public class TicketServiceImpl implements TicketService {

    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final int ADULT_TICKET_PRICE = 25;
    private static final int CHILD_TICKET_PRICE = 15;
    private static final int INFANT_TICKET_PRICE = 0;

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    public TicketServiceImpl(TicketPaymentService ticketPaymentService, SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    /**
     * Validates the purchase request, calculates total cost and required seats,
     * processes payment, and reserves seats for the given account.
     *
     * @param accountId          must be a non-null positive account identifier
     * @param ticketTypeRequests one or more ticket requests (max 25 tickets total;
     *                           children/infants require at least one adult, and
     *                           infants cannot outnumber adults)
     * @throws InvalidPurchaseException if any validation or business rule is violated
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccountId(accountId);
        Map<Type, Long> ticketCounts = aggregateAndValidateRequests(ticketTypeRequests);
        validateBusinessRules(ticketCounts);

        int totalAmountToPay = calculateTotalAmount(ticketCounts);
        int totalSeatsToAllocate = calculateTotalSeats(ticketCounts);

        ticketPaymentService.makePayment(accountId, totalAmountToPay);
        seatReservationService.reserveSeat(accountId, totalSeatsToAllocate);
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException();
        }
    }

    private Map<Type, Long> aggregateAndValidateRequests(TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException();
        }

        Map<Type, Long> ticketCounts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            ticketCounts.put(type, 0L);
        }

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null || request.getTicketType() == null || request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException();
            }
            ticketCounts.merge(request.getTicketType(), (long) request.getNoOfTickets(), Long::sum);
        }

        return ticketCounts;
    }

    private void validateBusinessRules(Map<Type, Long> ticketCounts) {
        long adultCount = ticketCounts.get(Type.ADULT);
        long childCount = ticketCounts.get(Type.CHILD);
        long infantCount = ticketCounts.get(Type.INFANT);
        long totalTickets = adultCount + childCount + infantCount;

        if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException();
        }

        if (adultCount == 0 && (childCount > 0 || infantCount > 0)) {
            throw new InvalidPurchaseException();
        }

        if (infantCount > adultCount) {
            throw new InvalidPurchaseException();
        }
    }

    private int calculateTotalAmount(Map<Type, Long> ticketCounts) {
        long total = (ticketCounts.get(Type.ADULT) * ADULT_TICKET_PRICE)
                + (ticketCounts.get(Type.CHILD) * CHILD_TICKET_PRICE)
                + (ticketCounts.get(Type.INFANT) * INFANT_TICKET_PRICE);
        return Math.toIntExact(total);
    }

    private int calculateTotalSeats(Map<Type, Long> ticketCounts) {
        long seats = ticketCounts.get(Type.ADULT) + ticketCounts.get(Type.CHILD);
        return Math.toIntExact(seats);
    }
}
