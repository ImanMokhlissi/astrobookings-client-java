# AstroBookings Client

Java console client for AstroBookings, a fictional space travel company.
Final exercise of the "Programación Inteligente" course. Not for production.

**For**: customers who browse rocket launches and book seats from a terminal.

**Does**: lists launches, registers users, books seats, shows bookings.

## API constraints: activity-bookings-express at http://localhost:3000

- Client says "launches", API says "activities": map them internally.
- `GET /activities` is public: id, name, slug, price, date, duration, location,
  minParticipants, maxParticipants, status. Ignore any other field.
- No rocket data: the API has no such field.
- `POST /users` {username, email, password, terms:true} returns
  {user, accessToken}. The token expires after 24 h.
- `POST /login` is broken (always 401): use the token from registration.
- `GET /bookings`, `GET /bookings/:id` and `POST /bookings`
  {activityId, participants} require `Authorization: Bearer <token>`.
- Free seats are not exposed. Over capacity returns 400 with
  "Capacity exceeded. Available: N, Requested: M".
- The API accepts draft bookings: offer only published or confirmed launches.
- All errors use the format {message, errors:[{field, message}]}.
