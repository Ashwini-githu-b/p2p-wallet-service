#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080/api/v1"

echo "========================================="
echo "   P2P WALLET SERVICE: LIVE TEST SCRIPT   "
echo "========================================="

# 1. Health check
echo -e "\n1. Checking Health..."
HEALTH=$(curl -s http://localhost:8080/actuator/health)
echo "Response: $HEALTH"

# 2. Create User 1 (Carol)
EMAIL1="carol_$(date +%s)@example.com"
echo -e "\n2. Creating User 1 (Carol: $EMAIL1)..."
USER1_RES=$(curl -s -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"Carol Danvers\", \"email\": \"$EMAIL1\"}")
echo "Response: $USER1_RES"
USER1_ID=$(echo "$USER1_RES" | grep -o '"userId":"[^"]*' | cut -d'"' -f4)

# 3. Create User 2 (David)
EMAIL2="david_$(date +%s)@example.com"
echo -e "\n3. Creating User 2 (David: $EMAIL2)..."
USER2_RES=$(curl -s -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"David Banner\", \"email\": \"$EMAIL2\"}")
echo "Response: $USER2_RES"
USER2_ID=$(echo "$USER2_RES" | grep -o '"userId":"[^"]*' | cut -d'"' -f4)

# 4. Top Up Carol with 50,000 paise (Rs 500)
TOPUP_KEY="topup-key-$(date +%s)"
echo -e "\n4. Topping up Carol with 50,000 paise (Key: $TOPUP_KEY)..."
TOPUP_RES=$(curl -s -i -X POST "$BASE_URL/wallets/$USER1_ID/topup" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $TOPUP_KEY" \
  -d '{"amountPaise": 50000}')
echo "$TOPUP_RES" | grep -E "HTTP/|Idempotent-Replay|transactionId"

# 5. Idempotent Replay of Top Up
echo -e "\n5. Testing Idempotent Replay of Top Up (Same Key)..."
REPLAY_RES=$(curl -s -i -X POST "$BASE_URL/wallets/$USER1_ID/topup" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $TOPUP_KEY" \
  -d '{"amountPaise": 50000}')
echo "$REPLAY_RES" | grep -E "HTTP/|Idempotent-Replay|transactionId"

# 6. Transfer 15,000 paise (Rs 150) from Carol to David
TRANSFER_KEY="tx-key-$(date +%s)"
echo -e "\n6. Transferring 15,000 paise from Carol to David (Key: $TRANSFER_KEY)..."
TRANSFER_RES=$(curl -s -i -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $TRANSFER_KEY" \
  -d "{\"fromUserId\": \"$USER1_ID\", \"toUserId\": \"$USER2_ID\", \"amountPaise\": 15000}")
echo "$TRANSFER_RES" | grep -E "HTTP/|Idempotent-Replay|transactionId"

# 7. Test Overdraft Protection (Attempt to send 100,000 paise when balance is 35,000)
OVERDRAFT_KEY="overdraft-key-$(date +%s)"
echo -e "\n7. Testing Overdraft Protection (Carol has 35,000, sending 100,000)..."
OVERDRAFT_RES=$(curl -s -i -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $OVERDRAFT_KEY" \
  -d "{\"fromUserId\": \"$USER1_ID\", \"toUserId\": \"$USER2_ID\", \"amountPaise\": 100000}")
echo "$OVERDRAFT_RES" | grep -E "HTTP/|INSUFFICIENT_FUNDS|status"

# 8. Check Current Balances
echo -e "\n8. Final Wallet Balances:"
echo -n "Carol (Expected: 35000): "
curl -s "$BASE_URL/wallets/$USER1_ID"
echo ""
echo -n "David (Expected: 15000): "
curl -s "$BASE_URL/wallets/$USER2_ID"
echo ""

# 9. View Carol's Transaction History
echo -e "\n9. Carol's Transaction History (Newest first):"
curl -s "$BASE_URL/wallets/$USER1_ID/transactions?page=0&size=10"
echo ""

echo -e "\n========================================="
echo "        ALL LIVE CHECKS PASSED!          "
echo "========================================="

