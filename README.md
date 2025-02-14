# Distributed Bank Transactions

A programming project implementing a distributed bank transaction system, handling constraints such as maximum account balance.

## Technologies
- Java
- Oracle XA
- JDBC (Java Database Connectivity)

## Database Access
⚠️ This project relies on a university-hosted database and requires internal network access.

If you cannot access the university network, you can still:
- Review the transaction handling logic in [OracleXaBank.java](./src/main/java/ch/unibas/dmi/dbis/fds/_2pc/OracleXaBank.java) and [AbstractOracleXaBank.java](./src/main/java/ch/unibas/dmi/dbis/fds/_2pc/AbstractOracleXaBank.java)
- Understand the tests implemented to ensure global atomicty in [XaBankingAppTest.java](./src/main/java/ch/unibas/dmi/dbis/fds/_2pc/XaBankingAppTest.java)

## Features
- Guaranteed global atomocity between transactions using the XA extensions of JDBC.
- Exception handling for error situations with proper rollback of the transaction.

## Future Improvements
- Improved Transaction Visualization
