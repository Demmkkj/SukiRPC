#include "rpc_outcome.h"

#include <cassert>
#include <thread>

int main() {
    RpcOutcome state;
    const auto beforeClear = state.begin();
    state.begin();
    assert(!state.complete(beforeClear, true));
    assert(state.read() == 0);

    const auto obsolete = state.begin();
    const auto current = state.begin();
    std::thread oldCallback([&] { assert(!state.complete(obsolete, true)); });
    assert(state.complete(current, false));
    oldCallback.join();
    assert(state.read() == -1);
    assert(!state.complete(current, true));
    assert(state.read() == -1);
    assert(state.complete(state.begin(), true));
    assert(state.read() == 1);
}
