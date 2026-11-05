# EARS

EARS (Easy Approach to Requirements Syntax) writes each acceptance criterion as one testable
sentence with a fixed structure. Use the pattern that fits, and keep one behavior per criterion.

| Pattern    | Use it for                            | Structure                                       |
|------------|---------------------------------------|-------------------------------------------------|
| Ubiquitous | Something that is always true         | The system SHALL {response}.                    |
| WHEN       | A response to an event                | WHEN {trigger}, the system SHALL {response}.    |
| IF/THEN    | A response to an unwanted situation   | IF {condition}, THEN the system SHALL {response}. |
| WHILE      | Behavior during a state               | WHILE {state}, the system SHALL {response}.     |
| WHERE      | Behavior of an optional feature       | WHERE {feature}, the system SHALL {response}.   |

## Examples

- **Ubiquitous**: The system SHALL show dates in ISO 8601 format.
- **WHEN**: WHEN the user selects "List items", the system SHALL show all available items.
- **IF/THEN**: IF the server is not reachable, THEN the system SHALL show a clear error message.
- **WHILE**: WHILE the user has no active session, the system SHALL hide the "Log out" option.
- **WHERE**: WHERE a proxy is configured, the system SHALL send all requests through it.
