# Cinema Tickets Service (Java)

Implementation of `TicketService` enforcing cinema ticket pricing, seat allocation, and purchasing business rules.

### How `purchaseTickets` Works

1. **`validateAccountId(accountId)`** — Rejects null or invalid account IDs (zero or negative numbers).
2. **`aggregateTickets(requests)`** — Checks that the request array, elements, ticket types and quantities are valid (rejecting `null` elements and zero or negative quantities). Combines requests for the same ticket type using an `EnumMap<Type, Long>`. Using `Long` helps prevent integer overflow when adding ticket quantities before checking the 25-ticket limit.
3. **`validateBusinessRules(ticketCounts)`** — Enforces the 25-ticket maximum, requires at least one adult when children or infants are included, and ensures infants do not outnumber adults.
4. **`calculateTotalAmount(ticketCounts)`** — Calculates the total price: adults × £25, children × £15 and infants × £0 (included explicitly so all ticket types are accounted for and makes future pricing changes easier). Uses `Math.toIntExact` to detect overflow when converting the total to an `int`.
5. **`calculateTotalSeatsToAllocate(ticketCounts)`** — Calculates the number of seats required (adults + children). Infants do not require a separate seat.
6. **Downstream Calls** — Processes the payment using `ticketPaymentService.makePayment(...)` and reserves seats via `seatReservationService.reserveSeat(...)`.
7. **Assumptions / Fail-Fast Validation:** If any validation or business rule check fails, `InvalidPurchaseException` is thrown immediately before any payment or seat reservation calls are made.

## Prerequisites & Running Tests
- **Java:** 21
- **Build Tool:** Maven

To compile and run the unit test suite:
```bash
mvn test