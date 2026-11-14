package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    private static final long VALID_ACCOUNT_ID = 1L;

    @Mock
    private TicketPaymentService ticketPaymentService;

    @Mock
    private SeatReservationService seatReservationService;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketPaymentService, seatReservationService);
    }

    @Nested
    @DisplayName("Valid Purchases")
    class ValidPurchases {

        @Test
        @DisplayName("Should calculate payment and reserve seat for a single adult ticket")
        void shouldPurchaseSingleAdultTicket() {
            TicketTypeRequest adultRequest = new TicketTypeRequest(Type.ADULT, 1);

            ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultRequest);

            verify(ticketPaymentService).makePayment(VALID_ACCOUNT_ID, 25);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 1);
            verifyNoMoreInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should charge adults and children only, and exclude infants from seat reservations")
        void shouldCalculateCorrectAmountAndSeatsForMixedTickets() {
            TicketTypeRequest adults = new TicketTypeRequest(Type.ADULT, 2);
            TicketTypeRequest children = new TicketTypeRequest(Type.CHILD, 3);
            TicketTypeRequest infants = new TicketTypeRequest(Type.INFANT, 2);

            ticketService.purchaseTickets(VALID_ACCOUNT_ID, adults, children, infants);

            // (2 * 25) + (3 * 15) + (2 * 0) = 95; seats = 2 + 3 = 5
            verify(ticketPaymentService).makePayment(VALID_ACCOUNT_ID, 95);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 5);
        }

        @Test
        @DisplayName("Should allow purchasing the exact maximum of 25 tickets including infants")
        void shouldAllowMaximumOf25Tickets() {
            TicketTypeRequest adults = new TicketTypeRequest(Type.ADULT, 10);
            TicketTypeRequest children = new TicketTypeRequest(Type.CHILD, 10);
            TicketTypeRequest infants = new TicketTypeRequest(Type.INFANT, 5);

            ticketService.purchaseTickets(VALID_ACCOUNT_ID, adults, children, infants);

            // (10 * 25) + (10 * 15) = 400; seats = 10 + 10 = 20
            verify(ticketPaymentService).makePayment(VALID_ACCOUNT_ID, 400);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 20);
        }

        @Test
        @DisplayName("Should aggregate multiple requests of the same ticket type")
        void shouldAggregateDuplicateTicketTypeRequests() {
            TicketTypeRequest firstAdultBatch = new TicketTypeRequest(Type.ADULT, 2);
            TicketTypeRequest secondAdultBatch = new TicketTypeRequest(Type.ADULT, 1);
            TicketTypeRequest infantBatch = new TicketTypeRequest(Type.INFANT, 3);

            ticketService.purchaseTickets(VALID_ACCOUNT_ID, firstAdultBatch, secondAdultBatch, infantBatch);

            verify(ticketPaymentService).makePayment(VALID_ACCOUNT_ID, 75);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 3);
        }

        @Test
        @DisplayName("Default constructor should instantiate without throwing")
        void shouldConstructWithDefaultThirdPartyServices() {
            TicketService defaultService = new TicketServiceImpl();
            assertDoesNotThrow(() -> defaultService.purchaseTickets(VALID_ACCOUNT_ID, new TicketTypeRequest(Type.ADULT, 1)));
        }
    }

    @Nested
    @DisplayName("Invalid Account IDs")
    class InvalidAccountIds {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {0L, -1L, -999L})
        @DisplayName("Should reject null, zero, or negative account IDs")
        void shouldThrowWhenAccountIdIsInvalid(Long invalidAccountId) {
            TicketTypeRequest request = new TicketTypeRequest(Type.ADULT, 1);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(invalidAccountId, request));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }
    }

    @Nested
    @DisplayName("Invalid Requests and Ticket Counts")
    class InvalidRequests {

        @Test
        @DisplayName("Should reject null varargs array")
        void shouldThrowWhenRequestsArrayIsNull() {
            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, (TicketTypeRequest[]) null));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject empty ticket requests")
        void shouldThrowWhenNoRequestsProvided() {
            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject null element inside ticket requests")
        void shouldThrowWhenAnyRequestElementIsNull() {
            TicketTypeRequest validAdult = new TicketTypeRequest(Type.ADULT, 1);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, validAdult, null));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject request with null ticket type")
        void shouldThrowWhenTicketTypeIsNull() {
            TicketTypeRequest nullTypeRequest = new TicketTypeRequest(null, 1);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, nullTypeRequest));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        @DisplayName("Should reject request with zero or negative ticket quantity")
        void shouldThrowWhenTicketCountIsZeroOrNegative(int invalidCount) {
            TicketTypeRequest invalidRequest = new TicketTypeRequest(Type.ADULT, invalidCount);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, invalidRequest));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject purchase exceeding 25 tickets total")
        void shouldThrowWhenTotalTicketsExceed25() {
            TicketTypeRequest adults = new TicketTypeRequest(Type.ADULT, 20);
            TicketTypeRequest children = new TicketTypeRequest(Type.CHILD, 6);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adults, children));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should safely reject integer overflow when summing huge ticket counts")
        void shouldThrowWithoutOverflowWhenSummingMaxIntegerRequests() {
            TicketTypeRequest firstHuge = new TicketTypeRequest(Type.ADULT, Integer.MAX_VALUE);
            TicketTypeRequest secondHuge = new TicketTypeRequest(Type.ADULT, Integer.MAX_VALUE);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, firstHuge, secondHuge));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }
    }

    @Nested
    @DisplayName("Adult Supervision Rules")
    class AdultSupervisionRules {

        @Test
        @DisplayName("Should reject child tickets purchased without an adult")
        void shouldThrowWhenPurchasingChildrenWithoutAdult() {
            TicketTypeRequest childrenOnly = new TicketTypeRequest(Type.CHILD, 2);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, childrenOnly));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject infant tickets purchased without an adult")
        void shouldThrowWhenPurchasingInfantsWithoutAdult() {
            TicketTypeRequest infantsOnly = new TicketTypeRequest(Type.INFANT, 1);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, infantsOnly));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject child and infant tickets combined without an adult")
        void shouldThrowWhenPurchasingChildrenAndInfantsWithoutAdult() {
            TicketTypeRequest children = new TicketTypeRequest(Type.CHILD, 2);
            TicketTypeRequest infants = new TicketTypeRequest(Type.INFANT, 1);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, children, infants));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }

        @Test
        @DisplayName("Should reject when infants outnumber adults (lap constraint)")
        void shouldThrowWhenInfantsOutnumberAdults() {
            TicketTypeRequest adults = new TicketTypeRequest(Type.ADULT, 2);
            TicketTypeRequest infants = new TicketTypeRequest(Type.INFANT, 3);

            assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adults, infants));

            verifyNoInteractions(ticketPaymentService, seatReservationService);
        }
    }
}