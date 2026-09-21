ALTER TABLE expense ADD COLUMN recurrence_frequency VARCHAR(20);
ALTER TABLE expense ADD COLUMN recurring_source_id BIGINT REFERENCES expense(id);
ALTER TABLE expense ADD COLUMN recurrence_period VARCHAR(10);

CREATE UNIQUE INDEX uq_expense_recurrence_occurrence
    ON expense(recurring_source_id, recurrence_period)
    WHERE recurring_source_id IS NOT NULL;
