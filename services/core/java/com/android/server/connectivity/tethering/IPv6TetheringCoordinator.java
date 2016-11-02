/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity.tethering;

import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkState;
import android.net.RouteInfo;
import android.util.Log;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedList;


/**
 * IPv6 tethering is rather different from IPv4 owing to the absence of NAT.
 * This coordinator is responsible for evaluating the dedicated prefixes
 * assigned to the device and deciding how to divvy them up among downstream
 * interfaces.
 *
 * @hide
 */
public class IPv6TetheringCoordinator {
    private static final String TAG = IPv6TetheringCoordinator.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private final ArrayList<TetherInterfaceStateMachine> mNotifyList;
    private final LinkedList<TetherInterfaceStateMachine> mActiveDownstreams;
    private NetworkState mUpstreamNetworkState;

    public IPv6TetheringCoordinator(ArrayList<TetherInterfaceStateMachine> notifyList) {
        mNotifyList = notifyList;
        mActiveDownstreams = new LinkedList<>();
    }

    public void addActiveDownstream(TetherInterfaceStateMachine downstream) {
        if (mActiveDownstreams.indexOf(downstream) == -1) {
            // Adding a new downstream appends it to the list. Adding a
            // downstream a second time without first removing it has no effect.
            mActiveDownstreams.offer(downstream);
            updateIPv6TetheringInterfaces();
        }
    }

    public void removeActiveDownstream(TetherInterfaceStateMachine downstream) {
        stopIPv6TetheringOn(downstream);
        if (mActiveDownstreams.remove(downstream)) {
            updateIPv6TetheringInterfaces();
        }
    }

    public void updateUpstreamNetworkState(NetworkState ns) {
        if (VDBG) {
            Log.d(TAG, "updateUpstreamNetworkState: " + toDebugString(ns));
        }
        if (!canTetherIPv6(ns)) {
            stopIPv6TetheringOnAllInterfaces();
            setUpstreamNetworkState(null);
            return;
        }

        if (mUpstreamNetworkState != null &&
            !ns.network.equals(mUpstreamNetworkState.network)) {
            stopIPv6TetheringOnAllInterfaces();
        }

        setUpstreamNetworkState(ns);
        updateIPv6TetheringInterfaces();
    }

    private void stopIPv6TetheringOnAllInterfaces() {
        for (TetherInterfaceStateMachine sm : mNotifyList) {
            stopIPv6TetheringOn(sm);
        }
    }

    private void setUpstreamNetworkState(NetworkState ns) {
        if (ns == null) {
            mUpstreamNetworkState = null;
        } else {
            // Make a deep copy of the parts we need.
            mUpstreamNetworkState = new NetworkState(
                    null,
                    new LinkProperties(ns.linkProperties),
                    new NetworkCapabilities(ns.networkCapabilities),
                    new Network(ns.network),
                    null,
                    null);
        }

        if (DBG) {
            Log.d(TAG, "setUpstreamNetworkState: " + toDebugString(mUpstreamNetworkState));
        }
    }

    private void updateIPv6TetheringInterfaces() {
        for (TetherInterfaceStateMachine sm : mNotifyList) {
            final LinkProperties lp = getInterfaceIPv6LinkProperties(sm);
            sm.sendMessage(TetherInterfaceStateMachine.CMD_IPV6_TETHER_UPDATE, 0, 0, lp);
            break;
        }
    }

    private LinkProperties getInterfaceIPv6LinkProperties(TetherInterfaceStateMachine sm) {
        if (mUpstreamNetworkState == null) return null;

        if (sm.interfaceType() == ConnectivityManager.TETHERING_BLUETOOTH) {
            // TODO: Figure out IPv6 support on PAN interfaces.
            return null;
        }

        // NOTE: Here, in future, we would have policies to decide how to divvy
        // up the available dedicated prefixes among downstream interfaces.
        // At this time we have no such mechanism--we only support tethering
        // IPv6 toward the oldest (first requested) active downstream.

        final TetherInterfaceStateMachine currentActive = mActiveDownstreams.peek();
        if (currentActive != null && currentActive == sm) {
            final LinkProperties lp = getIPv6OnlyLinkProperties(
                    mUpstreamNetworkState.linkProperties);
            if (lp.hasIPv6DefaultRoute() && lp.hasGlobalIPv6Address()) {
                return lp;
            }
        }

        return null;
    }

    private static boolean canTetherIPv6(NetworkState ns) {
        // Broadly speaking:
        //
        //     [1] does the upstream have an IPv6 default route?
        //
        // and
        //
        //     [2] does the upstream have one or more global IPv6 /64s
        //         dedicated to this device?
        //
        // In lieu of Prefix Delegation and other evaluation of whether a
        // prefix may or may not be dedicated to this device, for now just
        // check whether the upstream is TRANSPORT_CELLULAR. This works
        // because "[t]he 3GPP network allocates each default bearer a unique
        // /64 prefix", per RFC 6459, Section 5.2.

        final boolean canTether =
                (ns != null) && (ns.network != null) &&
                (ns.linkProperties != null) && (ns.networkCapabilities != null) &&
                // At least one upstream DNS server:
                ns.linkProperties.isProvisioned() &&
                // Minimal amount of IPv6 provisioning:
                ns.linkProperties.hasIPv6DefaultRoute() &&
                ns.linkProperties.hasGlobalIPv6Address() &&
                // Temporary approximation of "dedicated prefix":
                ns.networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

        // For now, we do not support separate IPv4 and IPv6 upstreams (e.g.
        // tethering with 464xlat involved). TODO: Rectify this shortcoming,
        // likely by calling NetworkManagementService#startInterfaceForwarding()
        // for all upstream interfaces.
        RouteInfo v4default = null;
        RouteInfo v6default = null;
        if (canTether) {
            for (RouteInfo r : ns.linkProperties.getAllRoutes()) {
                if (r.isIPv4Default()) {
                    v4default = r;
                } else if (r.isIPv6Default()) {
                    v6default = r;
                }

                if (v4default != null && v6default != null) {
                    break;
                }
            }
        }

        final boolean supportedConfiguration =
                (v4default != null) && (v6default != null) &&
                (v4default.getInterface() != null) &&
                v4default.getInterface().equals(v6default.getInterface());

        final boolean outcome = canTether && supportedConfiguration;

        if (VDBG) {
            if (ns == null) {
                Log.d(TAG, "No available upstream.");
            } else {
                Log.d(TAG, String.format("IPv6 tethering is %s for upstream: %s",
                        (outcome ? "available" : "not available"), toDebugString(ns)));
            }
        }

        return outcome;
    }

    private static LinkProperties getIPv6OnlyLinkProperties(LinkProperties lp) {
        final LinkProperties v6only = new LinkProperties();
        if (lp == null) {
            return v6only;
        }

        // NOTE: At this time we don't copy over any information about any
        // stacked links. No current stacked link configuration has IPv6.

        v6only.setInterfaceName(lp.getInterfaceName());

        v6only.setMtu(lp.getMtu());

        for (LinkAddress linkAddr : lp.getLinkAddresses()) {
            if (linkAddr.isGlobalPreferred() && linkAddr.getPrefixLength() == 64) {
                v6only.addLinkAddress(linkAddr);
            }
        }

        for (RouteInfo routeInfo : lp.getRoutes()) {
            final IpPrefix destination = routeInfo.getDestination();
            if ((destination.getAddress() instanceof Inet6Address) &&
                (destination.getPrefixLength() <= 64)) {
                v6only.addRoute(routeInfo);
            }
        }

        for (InetAddress dnsServer : lp.getDnsServers()) {
            if (isIPv6GlobalAddress(dnsServer)) {
                // For now we include ULAs.
                v6only.addDnsServer(dnsServer);
            }
        }

        v6only.setDomains(lp.getDomains());

        return v6only;
    }

    // TODO: Delete this and switch to LinkAddress#isGlobalPreferred once we
    // announce our own IPv6 address as DNS server.
    private static boolean isIPv6GlobalAddress(InetAddress ip) {
        return (ip instanceof Inet6Address) &&
               !ip.isAnyLocalAddress() &&
               !ip.isLoopbackAddress() &&
               !ip.isLinkLocalAddress() &&
               !ip.isSiteLocalAddress() &&
               !ip.isMulticastAddress();
    }

    private static String toDebugString(NetworkState ns) {
        if (ns == null) {
            return "NetworkState{null}";
        }
        return String.format("NetworkState{%s, %s, %s}",
                ns.network,
                ns.networkCapabilities,
                ns.linkProperties);
    }

    private static void stopIPv6TetheringOn(TetherInterfaceStateMachine sm) {
        sm.sendMessage(TetherInterfaceStateMachine.CMD_IPV6_TETHER_UPDATE, 0, 0, null);
    }
}
