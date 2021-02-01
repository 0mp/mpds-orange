-- used for oauth2 authorized client persistence
CREATE TABLE IF NOT EXISTS cluster_performance_benchmark (
                                        id SERIAL PRIMARY KEY,
                                        num_taskmanager_pods int NOT NULL,
                                        max_rate int NOT NULL,
                                        parallelization int NOT NULL,
                                        restart_time int,
                                        catchup_time int,
                                        recovery_time int,
                                        created_date timestamp DEFAULT NULL,
                                        created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL
);
