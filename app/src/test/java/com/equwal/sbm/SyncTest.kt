package com.equwal.sbm

import net.jqwik.api.Example

class SyncTest {
    @Example
    fun `an address without a scheme gets https`() {
        check(Sync.address(" sbm.example.org/ ") == "https://sbm.example.org")
    }

    @Example
    fun `an address with a scheme stays`() {
        check(Sync.address("https://sbm.example.org") == "https://sbm.example.org")
        check(Sync.address("http://10.0.2.2:8750/") == "http://10.0.2.2:8750")
    }
}
