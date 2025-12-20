# Build Stage
FROM golang:1.23-alpine AS builder

WORKDIR /app

# Copy go mod and sum files
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY *.go ./
COPY crypto ./crypto/

# Build the application
# CGO_ENABLED=0 for static binary
RUN CGO_ENABLED=0 GOOS=linux go build -o main .

# Run Stage
FROM alpine:latest

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/main .

# Copy necessary assets (e.g., users.json if static, though it should probably be in a persistent volume or secret)
# For this "demo/prototype", we'll copy it, but warn about persistence.
COPY users.json .

# Expose port (Cloud Run defaults to 8080)
EXPOSE 8080

# Command to run
CMD ["./main"]
