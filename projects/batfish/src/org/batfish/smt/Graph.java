package org.batfish.smt;

import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.*;
import org.batfish.datamodel.collections.EdgeSet;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.smt.utils.Table2;

import java.util.*;


public class Graph {

    private IBatfish _batfish;

    private Map<String, Configuration> _configurations;

    private Map<String, Set<Long>> _areaIds;

    private Map<String, Map<String, List<StaticRoute>>> _staticRoutes;

    private Map<String, Set<String>> _neighbors;

    private Map<String, List<GraphEdge>> _edgeMap;

    private Map<GraphEdge, GraphEdge> _otherEnd;

    private Map<GraphEdge, BgpNeighbor> _ebgpNeighbors;

    private Table2<String, String, BgpNeighbor> _ibgpNeighbors;

    public Graph(IBatfish batfish) {
        this(batfish, null);
    }

    public Graph(IBatfish batfish, Set<String> routers) {
        _batfish = batfish;
        _configurations = new HashMap<>(_batfish.loadConfigurations());
        _edgeMap = new HashMap<>();
        _otherEnd = new HashMap<>();
        _areaIds = new HashMap<>();
        _staticRoutes = new HashMap<>();
        _neighbors = new HashMap<>();
        _ebgpNeighbors = new HashMap<>();
        _ibgpNeighbors = new Table2<>();

        // Remove the routers we don't want to model
        if (routers != null) {
            List<String> toRemove = new ArrayList<>();
            _configurations.forEach((router, conf) -> {
                if (!routers.contains(router)) {
                    toRemove.add(router);
                }
            });
            for (String router : toRemove) {
                _configurations.remove(router);
            }
        }

        initGraph();
        initStaticRoutes();
        initEbgpNeighbors();
        initIbgpNeighbors();
        initAreaIds();
    }

    private void initGraph() {
        Topology topology = _batfish.computeTopology(_configurations);
        Map<NodeInterfacePair, Interface> ifaceMap = new HashMap<>();
        Map<String, Set<NodeInterfacePair>> routerIfaceMap = new HashMap<>();

        _configurations.forEach((router, conf) -> {
            Set<NodeInterfacePair> ifacePairs = new HashSet<>();
            conf.getInterfaces().forEach((name, iface) -> {
                NodeInterfacePair nip = new NodeInterfacePair(router, name);
                ifacePairs.add(nip);
                ifaceMap.put(nip, iface);
            });
            routerIfaceMap.put(router, ifacePairs);
        });

        Map<NodeInterfacePair, EdgeSet> ifaceEdges = topology.getInterfaceEdges();

        _neighbors = new HashMap<>();
        routerIfaceMap.forEach((router, nips) -> {
            List<GraphEdge> graphEdges = new ArrayList<>();

            Set<String> neighs = new HashSet<>();
            nips.forEach((nip) -> {
                EdgeSet es = ifaceEdges.get(nip);
                Interface i1 = ifaceMap.get(nip);
                boolean hasNoOtherEnd = (es == null && i1.getPrefix() != null);
                if (hasNoOtherEnd) {
                    GraphEdge ge = new GraphEdge(i1, null, router, null, false);
                    graphEdges.add(ge);
                }

                if (es != null) {
                    boolean hasMultipleEnds = (es.size() > 2);
                    if (hasMultipleEnds) {
                        GraphEdge ge = new GraphEdge(i1, null, router, null, false);
                        graphEdges.add(ge);
                    } else {
                        for (Edge e : es) {
                            // System.out.println("  edge: " + e.toString());
                            if (!router.equals(e.getNode2())) {
                                Interface i2 = ifaceMap.get(e.getInterface2());
                                String neighbor = e.getNode2();
                                GraphEdge ge1 = new GraphEdge(i1, i2, router, neighbor, false);
                                GraphEdge ge2 = new GraphEdge(i2, i1, neighbor, router, false);
                                _otherEnd.put(ge1, ge2);
                                graphEdges.add(ge1);
                                neighs.add(neighbor);
                            }
                        }
                    }
                }
            });

            _edgeMap.put(router, graphEdges);
            _neighbors.put(router, neighs);
        });
    }

    private void initStaticRoutes() {
        _configurations.forEach((router, conf) -> {
            Map<String, List<StaticRoute>> map = new HashMap<>();
            _staticRoutes.put(router, map);
            for (GraphEdge ge : _edgeMap.get(router)) {
                Interface here = ge.getStart();
                Interface there = ge.getEnd();

                for (StaticRoute sr : conf.getDefaultVrf().getStaticRoutes()) {

                    // Check if next-hop interface is specified
                    String hereName = here.getName();
                    if (hereName.equals(sr.getNextHopInterface())) {
                        List<StaticRoute> srs = map.getOrDefault(hereName, new ArrayList<>());
                        srs.add(sr);
                        map.put(hereName, srs);
                    }

                    // Check if next-hop ip corresponds to direct interface
                    Ip nhIp = sr.getNextHopIp();
                    boolean isNextHop = nhIp != null && there != null && there.getPrefix() !=
                            null && there.getPrefix().getAddress().equals(nhIp);

                    if (isNextHop) {
                        List<StaticRoute> srs = map.getOrDefault(hereName, new ArrayList<>());
                        srs.add(sr);
                        map.put(there.getName(), srs);
                    }
                }
            }
        });
    }

    private void initEbgpNeighbors() {
        Map<String, List<Ip>> ips = new HashMap<>();
        Map<String, List<BgpNeighbor>> neighbors = new HashMap<>();

        _configurations.forEach((router, conf) -> {
            List<Ip> ipList = new ArrayList<>();
            List<BgpNeighbor> ns = new ArrayList<>();
            ips.put(router, ipList);
            neighbors.put(router, ns);
            if (conf.getDefaultVrf().getBgpProcess() != null) {
                conf.getDefaultVrf().getBgpProcess().getNeighbors().forEach((pfx, neighbor) -> {
                    ipList.add(neighbor.getAddress());
                    ns.add(neighbor);
                });
            }
        });

        _configurations.forEach((router, conf) -> {
            List<Ip> ipList = ips.get(router);
            List<BgpNeighbor> ns = neighbors.get(router);
            if (conf.getDefaultVrf().getBgpProcess() != null) {
                _edgeMap.get(router).forEach(ge -> {
                    for (int i = 0; i < ipList.size(); i++) {
                        Ip ip = ipList.get(i);
                        BgpNeighbor n = ns.get(i);
                        Interface iface = ge.getStart();
                        if (ip != null && iface.getPrefix().contains(ip)) {
                            _ebgpNeighbors.put(ge, n);
                        }
                    }
                });
            }
        });
    }

    private Interface createIbgpInterface(BgpNeighbor n) {
        Interface iface = new Interface("iBGP-" + n.getLocalIp());
        iface.setActive(true);
        iface.setPrefix(n.getPrefix());
        return iface;
    }

    // TODO: very inefficient
    private void initIbgpNeighbors() {
        Map<String, Ip> ips = new HashMap<>();

        // Match iBGP sessions with pairs of routers and BgpNeighbor
        _configurations.forEach((router, conf) -> {
            BgpProcess p = conf.getDefaultVrf().getBgpProcess();
            if (p != null) {
                p.getNeighbors().forEach((pfx, n) -> {
                    ips.put(router, n.getLocalIp());
                });
            }
        });

        _configurations.forEach((router, conf) -> {
            BgpProcess p = conf.getDefaultVrf().getBgpProcess();
            if (p != null) {
                p.getNeighbors().forEach((pfx, n) -> {
                    if (n.getLocalAs().equals(n.getRemoteAs())) {
                        ips.forEach((r, ip) -> {
                            if (!router.equals(r) && pfx.contains(ip)) {
                                _ibgpNeighbors.put(router, r, n);
                            }
                        });
                    }
                });
            }
        });

        // Add abstract graph edges for iBGP sessions
        Table2<String, String, GraphEdge> reverse = new Table2<>();

        _ibgpNeighbors.forEach((r1, r2, n1) -> {
            Interface iface1 = createIbgpInterface(n1);

            BgpNeighbor n2 = _ibgpNeighbors.get(r2, r1);

            GraphEdge ge;
            if (n2 != null) {
                Interface iface2 = createIbgpInterface(n2);
                ge = new GraphEdge(iface1, iface2, r1, r2, true);
            } else {
                ge = new GraphEdge(iface1, null, r1, null, true);
            }

            reverse.put(r1, r2, ge);

            List<GraphEdge> edges = _edgeMap.get(r1);
            if (edges != null) {
                edges.add(ge);
            } else {
                edges = new ArrayList<>();
                edges.add(ge);
                _edgeMap.put(r1, edges);
            }

        });

        // Add other end to ibgp edges
        reverse.forEach((r1, r2, ge1) -> {
            GraphEdge ge2 = reverse.get(r2, r1);
            _otherEnd.put(ge1, ge2);
        });
    }

    private void initAreaIds() {
        _configurations.forEach((router, conf) -> {
            Set<Long> areaIds = new HashSet<>();
            OspfProcess p = conf.getDefaultVrf().getOspfProcess();
            if (p != null) {
                p.getAreas().forEach((id, area) -> {
                    areaIds.add(id);
                });
            }
            _areaIds.put(router, areaIds);
        });

    }

    boolean isInterfaceActive(RoutingProtocol proto, Interface iface) {
        if (proto == RoutingProtocol.OSPF) {
            return iface.getActive() && iface.getOspfEnabled();
        }
        return iface.getActive();
    }

    boolean isInterfaceUsed(Configuration conf, RoutingProtocol proto, Interface iface) {
        // Only use specified edges from static routes
        if (proto == RoutingProtocol.STATIC) {
            List<StaticRoute> srs = getStaticRoutes().get(conf.getName()).get(iface.getName());
            return iface.getActive() && srs != null && srs.size() > 0;
        }
        // Exclude abstract iBGP edges from all protocols except BGP
        if (iface.getName().startsWith("iBGP-")) {
            return (proto == RoutingProtocol.BGP);
        }
        // Never use Loopbacks for any protocol except connected
        if (iface.isLoopback(conf.getConfigurationFormat())) {
            return (proto == RoutingProtocol.CONNECTED);
        }
        return true;
    }

    Map<String, Map<String, List<StaticRoute>>> getStaticRoutes() {
        return _staticRoutes;
    }

    RoutingPolicy findCommonRoutingPolicy(String router, RoutingProtocol proto) {
        Configuration conf = _configurations.get(router);
        if (proto == RoutingProtocol.OSPF) {
            String exp = conf.getDefaultVrf().getOspfProcess().getExportPolicy();
            return conf.getRoutingPolicies().get(exp);
        }
        if (proto == RoutingProtocol.BGP) {
            for (Map.Entry<String, RoutingPolicy> entry : conf.getRoutingPolicies().entrySet()) {
                String name = entry.getKey();
                if (name.contains(EncoderSlice.BGP_COMMON_FILTER_LIST_NAME)) {
                    return entry.getValue();
                }
            }
            return null;
        }
        if (proto == RoutingProtocol.STATIC) {
            return null;
        }
        if (proto == RoutingProtocol.CONNECTED) {
            return null;
        }
        throw new BatfishException("TODO: findCommonRoutingPolicy for " + proto.protocolName());
    }

    RoutingPolicy findImportRoutingPolicy(String router, RoutingProtocol proto,
            LogicalEdge e) {
        Configuration conf = _configurations.get(router);
        if (proto == RoutingProtocol.CONNECTED) {
            return null;
        }
        if (proto == RoutingProtocol.STATIC) {
            return null;
        }
        if (proto == RoutingProtocol.OSPF) {
            return null;
        }
        if (proto == RoutingProtocol.BGP) {
            BgpNeighbor n = getEbgpNeighbors().get(e.getEdge());
            if (n == null || n.getImportPolicy() == null) {
                return null;
            }
            return conf.getRoutingPolicies().get(n.getImportPolicy());
        }
        throw new BatfishException("TODO: findImportRoutingPolicy: " + proto.protocolName());
    }

    Map<GraphEdge, BgpNeighbor> getEbgpNeighbors() {
        return _ebgpNeighbors;
    }

    Table2<String, String, BgpNeighbor> getIbgpNeighbors() {
        return _ibgpNeighbors;
    }

    RoutingPolicy findExportRoutingPolicy(String router, RoutingProtocol proto, LogicalEdge e) {
        Configuration conf = _configurations.get(router);
        if (proto == RoutingProtocol.CONNECTED) {
            return null;
        }
        if (proto == RoutingProtocol.STATIC) {
            return null;
        }
        if (proto == RoutingProtocol.OSPF) {
            String exp = conf.getDefaultVrf().getOspfProcess().getExportPolicy();
            return conf.getRoutingPolicies().get(exp);
        }
        if (proto == RoutingProtocol.BGP) {
            BgpNeighbor n = getEbgpNeighbors().get(e.getEdge());

            // if no neighbor (e.g., loopback), or no export policy
            if (n == null || n.getExportPolicy() == null) {
                return null;
            }

            return conf.getRoutingPolicies().get(n.getExportPolicy());
        }
        throw new BatfishException("TODO: findExportRoutingPolicy for " + proto.protocolName());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=======================================================\n");
        sb.append("---------- Router to edges map ----------\n");
        _edgeMap.forEach((router, graphEdges) -> {
            sb.append("Router: ").append(router).append("\n");
            graphEdges.forEach(edge -> {
                sb.append("  edge from: ").append(edge.getStart().getName());
                if (edge.getEnd() == null) {
                    sb.append(" to: null \n");
                } else {
                    sb.append(" to: ").append(edge.getEnd().getName()).append("\n");
                }
            });
        });

        sb.append("---------- Neighbors of each router ----------\n");
        _neighbors.forEach((router, peers) -> {
            sb.append("Router: ").append(router).append("\n");
            for (String peer : peers) {
                sb.append("  peer: ").append(peer).append("\n");
            }
        });

        sb.append("---------------- eBGP Neighbors ----------------\n");
        _ebgpNeighbors.forEach((ge, n) -> {
            sb.append("Edge: ").append(ge).append(" (").append(n.getAddress()).append(")").append("\n");
        });

        sb.append("---------------- iBGP Neighbors ----------------\n");
        _ibgpNeighbors.forEach((r1, r2, n) -> {
            sb.append("Edge: ").append(r1).append(" -> ").append(r2).append("\n");
        });

        sb.append("---------- Static Routes by Interface ----------\n");
        _staticRoutes.forEach((router, map) -> {
            map.forEach((iface, srs) -> {
                for (StaticRoute sr : srs) {
                    sb.append("Router: ").append(router).append(", Interface: ").append(iface)
                      .append(" --> ").append(sr.getNetwork().toString()).append("\n");
                }
            });
        });

        sb.append("=======================================================\n");
        return sb.toString();
    }

    public Map<String, Set<Long>> getAreaIds() {
        return _areaIds;
    }

    public Map<String, Configuration> getConfigurations() {
        return _configurations;
    }

    public Map<String, Set<String>> getNeighbors() {
        return _neighbors;
    }

    public Map<String, List<GraphEdge>> getEdgeMap() {
        return _edgeMap;
    }

    public Map<GraphEdge, GraphEdge> getOtherEnd() {
        return _otherEnd;
    }

}
