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

            verify(ticketPaymentService).makePayment(VALID_ACCOUNT_ID, 95);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 5);
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
}