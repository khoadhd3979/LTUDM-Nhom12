feat(phase1): complete TCP server foundation, Maven refactoring and NDJSON protocol

- Refactor project structure to standard Maven layout (com.securechat.*)
- Migrate legacy source files to _legacy/ backup directory
- Integrate dotenv-java for environment variables (.env & .env.example)
- Implement TCP multi-threaded server using ExecutorService (ServerMain, ClientHandler)
- Implement Newline-Delimited JSON protocol (Packet, MessageType, ProtocolSerializer)
- Implement Phase 1 PING/PONG heartbeat and error handling (BAD_JSON, UNKNOWN_TYPE)
- Refactor DBConnection facade, SecurityUtils (BCrypt), MailUtils, UserDAO, and UserDTO
- Update pom.xml with maven-shade-plugin for Fat JAR packaging (excluding signature files)