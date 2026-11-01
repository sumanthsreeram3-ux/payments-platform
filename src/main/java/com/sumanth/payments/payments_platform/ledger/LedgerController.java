package com.sumanth.payments.payments_platform.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    public record AccountBalance(String account, String type, long netDebitMinor) {}
    public record BalancesResponse(List<AccountBalance> accounts, boolean balanced) {}

    private final JdbcTemplate jdbc;

    public LedgerController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/balances")
    public BalancesResponse balances() {
        List<AccountBalance> list = jdbc.query("""
                SELECT a.name, a.type,
                       COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_minor
                                         ELSE -e.amount_minor END), 0) AS net_debit
                FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.name, a.type
                ORDER BY a.name
                """,
                (rs, i) -> new AccountBalance(rs.getString("name"), rs.getString("type"),
                        rs.getLong("net_debit")));
        long total = list.stream().mapToLong(AccountBalance::netDebitMinor).sum();
        return new BalancesResponse(list, total == 0);
    }
}
