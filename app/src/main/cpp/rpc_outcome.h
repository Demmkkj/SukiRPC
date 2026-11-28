#pragma once

#include <atomic>
#include <cstdint>

// The generation and its result share a single compare-and-exchange. A callback
// from a cleared or superseded activity cannot overwrite the current result.
class RpcOutcome {
public:
    uint64_t begin() {
        auto previous = state_.load();
        uint64_t pending;
        do {
            pending = (previous & ~resultMask) + (resultMask + 1);
        } while (!state_.compare_exchange_weak(previous, pending));
        return pending;
    }

    bool complete(uint64_t pending, bool success) {
        return state_.compare_exchange_strong(pending, pending | (success ? succeeded : failed));
    }

    int read() const {
        const auto result = state_.load() & resultMask;
        return result == succeeded ? 1 : (result == failed ? -1 : 0);
    }

private:
    static constexpr uint64_t resultMask = 3;
    static constexpr uint64_t succeeded = 1;
    static constexpr uint64_t failed = 2;
    std::atomic<uint64_t> state_{0};
};
