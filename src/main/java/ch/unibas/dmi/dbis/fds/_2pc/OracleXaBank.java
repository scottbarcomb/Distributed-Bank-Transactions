package ch.unibas.dmi.dbis.fds._2pc;


import com.sun.org.apache.xpath.internal.operations.Bool;
import oracle.jdbc.xa.OracleXAException;

import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * Check the XA stuff here --> https://docs.oracle.com/cd/B14117_01/java.101/b10979/xadistra.htm
 *
 * @author Alexander Stiemer (alexander.stiemer at unibas.ch)
 */
public class OracleXaBank extends AbstractOracleXaBank {


    public OracleXaBank( final String BIC, final String jdbcConnectionString, final String dbmsUsername, final String dbmsPassword ) throws SQLException {
        super( BIC, jdbcConnectionString, dbmsUsername, dbmsPassword );
    }


    @Override
    public float getBalance( final String iban ) throws SQLException {
        // TODO: your turn ;-)

        XAConnection xaConnection = this.getXaConnection();

        try (Connection connection = xaConnection.getConnection()) {
            // create the query to get the balance from the database
            String query = "SELECT Balance FROM account WHERE IBAN = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, iban);
                // execute the query and return the result
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) { // query returned a result in the set
                        return resultSet.getFloat("Balance");
                    } else {
                        throw new SQLException("Account with IBAN " + iban + " not found.");
                    }
                }
            }
        }
    }


    @Override
    public void transfer( final AbstractOracleXaBank TO_BANK, final String ibanFrom, final String ibanTo, final float value ) {
        // TODO: your turn ;-)

        // 1. Start transactions on both banks
        // 2. Debit this bank (ibanFrom and value)
        // 3. Credit the TO_BANK (ibanTo and value)
        // 4. End the transactions
        // 5. Prepare the transactions
        // 6. Commit or rollback

        if (value <= 0f) throw new RuntimeException("Transfer failed: Negative or zero transfer value.");

        XAConnection fromXaConnection;
        XAConnection toXaConnection;
        XAResource fromResource = null;
        XAResource toResource = null;
        Xid fromId = null;
        Xid toId = null;

        try {
            // Get connections and resources
            fromXaConnection = this.getXaConnection();
            toXaConnection = TO_BANK.getXaConnection();
            fromResource = fromXaConnection.getXAResource();
            toResource = toXaConnection.getXAResource();

            // 1. Start transactions on both banks
            fromId = this.startTransaction();
            toId = TO_BANK.startTransaction(fromId);

            // 2. Debit this bank (ibanFrom and value)
            try (Connection connection = fromXaConnection.getConnection()) {
                String update = "UPDATE account SET Balance = Balance - ? WHERE IBAN = ? AND Balance >= ?";
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setFloat(1, value);
                    statement.setString(2, ibanFrom);
                    statement.setFloat(3, value);

                    int rowsAffected = statement.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new SQLException("Insufficient funds or invalid IBAN: " + ibanFrom);
                    }
                }
            }

            // 3. Credit the TO_BANK (ibanTo and value)
            try (Connection connection = toXaConnection.getConnection()) {
                String update = "UPDATE account SET Balance = Balance + ? WHERE IBAN = ?";
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setFloat(1, value);
                    statement.setString(2, ibanTo);

                    int rowsAffected = statement.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new SQLException("Invalid IBAN: " + ibanTo);
                    }
                }
            }

            // 4. End the transactions
            this.endTransaction(fromId, false);
            TO_BANK.endTransaction(toId, false);

            // 5. Prepare the transactions
            /*
             * For Transfer of Coordination 2PC, the initial coordinator ('this') would
             * send a prepare message and a transfer-of-role message to the TO_BANK, making
             * the TO_BANK the new coordinator and 'this' the agent. The TO_BANK then does
             * the following:
             *
             * 1. Prepares to commit.
             * 2. Becomes the coordinator.
             * 3. Decides to commit or rollback both transactions.
             *
             * This process isn't supported by XA because XA has no way to transfer the role
             * of coordinator between agents.
             */
            int fromPrepare = fromResource.prepare(fromId);
            int toPrepare = toResource.prepare(toId);

            // 6. Commit or rollback
            /*
             * For Presumed Abort 2PC, if the coordinator crashes here after sending
             * the prepare-to-commit command to the agents, the agents will query the coordinator
             * after they time out. Since the coordinator has no log of the decision, it assumes
             * the transaction has failed and will send a command to abort the transaction (the
             * following else statement, rollback).
             *
             * This is not supported by XA, since XA offers no way for the agents to send a query
             * asking about the decision after the coordinator calls XAResource.prepare(XID).
             *
             * In Transfer of Coordination 2PC, TO_BANK is now the coordinator and makes the commit
             * decision, letting the original coordinator ('this') know if it should commit or
             * roll back.
             */
            if (fromPrepare == XAResource.XA_OK && toPrepare == XAResource.XA_OK) {
                fromResource.commit(fromId, false); // not one-phase
                toResource.commit(toId, false);
            } else {
                /*
                 * In Presumed Abort 2PC, this else statement will be entered if the coordinator
                 * crashes and there is no log to record the prepare-to-commit decision, resulting
                 * in a rollback.
                 */
                if (fromPrepare != XAResource.XA_OK) {
                    fromResource.rollback(fromId);
                }
                if (toPrepare != XAResource.XA_OK) {
                    toResource.rollback(toId);
                }
                throw new XAException("Prepare phase failed.");
            }

        } catch (XAException | SQLException ex) {
            // Attempt to rollback started transactions
            if (fromResource != null && fromId != null) {
                try {
                    this.endTransaction(fromId, true);
                    fromResource.rollback(fromId);
                } catch (XAException xae) {
                    System.err.println("Failed to rollback fromId: " + xae.getMessage());
                    xae.printStackTrace();
                }
            }

            if (toResource != null && toId != null) {
                try {
                    this.endTransaction(toId, true);
                    toResource.rollback(toId);
                } catch (XAException xae) {
                    System.err.println("Failed to rollback toId: " + xae.getMessage());
                    xae.printStackTrace();
                }
            }

            throw new RuntimeException("Transfer failed: " + ex.getMessage(), ex);
        }
    }
}
