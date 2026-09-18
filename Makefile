# Developer entry point for the whole stack.
#
# The repository holds two toolchains - Maven for the API, npm for the frontend - plus
# Docker for Postgres and Redis. This file is the single place that knows how to start
# them, so a new developer does not have to assemble the sequence from the README.
#
# Run `make` or `make help` to list targets.

SHELL := /bin/bash
BACKEND_DIR := backend
FRONTEND_DIR := frontend

.DEFAULT_GOAL := help
.PHONY: help setup env-check up down reset dev api web build test test-api test-web clean logs psql redis-cli

help: ## Show available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "} {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

setup: ## Install frontend dependencies and create .env files from the examples
	@cd $(FRONTEND_DIR) && npm install
	@[ -f $(BACKEND_DIR)/.env ] || { cp $(BACKEND_DIR)/.env.example $(BACKEND_DIR)/.env && echo "created $(BACKEND_DIR)/.env"; }
	@[ -f $(FRONTEND_DIR)/.env ] || { cp $(FRONTEND_DIR)/.env.example $(FRONTEND_DIR)/.env && echo "created $(FRONTEND_DIR)/.env"; }
	@$(MAKE) --no-print-directory env-check

# Reports keys added to an .env.example that are missing from the .env beside it.
#
# The copy above only runs when .env does not exist, so once created it never gains
# keys introduced later. Those keys then fall back to their application.yml defaults
# silently - the setting appears configurable, edits to .env.example look effective,
# and nothing reports otherwise. This turns that into a visible message.
#
# Deliberately reports instead of merging: a real .env holds secrets and local edits,
# and no convenience is worth a target that can rewrite it.
env-check: ## Report config keys present in .env.example but missing from .env
	@for dir in $(BACKEND_DIR) $(FRONTEND_DIR); do \
		[ -f $$dir/.env ] || continue; \
		missing=""; \
		for key in $$(grep -hoE '^[A-Z_][A-Z0-9_]*=' $$dir/.env.example | tr -d '='); do \
			grep -qE "^[[:space:]]*$$key=" $$dir/.env || missing="$$missing $$key"; \
		done; \
		if [ -n "$$missing" ]; then \
			echo "WARNING: $$dir/.env is missing keys added to .env.example since it was created:"; \
			for key in $$missing; do echo "    $$key"; done; \
			echo "  These fall back to defaults. Copy the lines you need from $$dir/.env.example."; \
		fi; \
	done

up: ## Start Postgres and Redis, and wait until both are healthy
	@docker compose up -d
	@printf "waiting for containers to report healthy"
	@for i in $$(seq 1 60); do \
		if [ "$$(docker compose ps --format '{{.Health}}' | grep -c healthy)" = "2" ]; then \
			echo " ok"; exit 0; \
		fi; \
		printf "."; sleep 1; \
	done; \
	echo " timed out"; docker compose ps; exit 1

down: ## Stop containers, keeping data volumes
	@docker compose down

reset: ## Stop containers AND delete their data - destroys the local database
	@docker compose down -v

dev: up ## Start everything: containers, API, and frontend together
	@$(MAKE) --no-print-directory env-check
	@echo "API      http://localhost:8080"
	@echo "Frontend http://localhost:5173"
	@echo "Press Ctrl-C to stop both."
	@trap 'kill 0' EXIT INT TERM; \
		(cd $(BACKEND_DIR) && set -a && { [ -f .env ] && . ./.env; }; set +a && mvn -q spring-boot:run) & \
		(cd $(FRONTEND_DIR) && npm run dev) & \
		wait

api: up ## Run only the API
	@$(MAKE) --no-print-directory env-check
	@cd $(BACKEND_DIR) && set -a && { [ -f .env ] && . ./.env; }; set +a && mvn spring-boot:run

web: ## Run only the frontend dev server
	@cd $(FRONTEND_DIR) && npm run dev

build: ## Build both the API jar and the frontend bundle
	@cd $(BACKEND_DIR) && mvn -q clean package
	@cd $(FRONTEND_DIR) && npm run build

test: test-api test-web ## Run all checks

test-api: ## Run the backend test suite
	@cd $(BACKEND_DIR) && mvn -q clean verify

test-web: ## Type-check and lint the frontend
	@cd $(FRONTEND_DIR) && npm run check

logs: ## Tail container logs
	@docker compose logs -f

psql: ## Open a psql shell against the dev database
	@docker compose exec postgres psql -U urlshortener -d urlshortener

redis-cli: ## Open a redis-cli shell
	@docker compose exec redis redis-cli

clean: ## Remove build output from both toolchains
	@cd $(BACKEND_DIR) && mvn -q clean
	@rm -rf $(FRONTEND_DIR)/dist $(FRONTEND_DIR)/node_modules
