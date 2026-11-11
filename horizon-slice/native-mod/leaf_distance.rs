// Compiled by horizon-mixins as a local function at the top of GameLevel::native_leaves_decay, which the game
// calls when a non-persistent leaf reaches vanilla's distance 7 (no log within 6 steps through leaves) on a
// random tick. Vanilla's state only counts to 7, so this looks further itself: a log reachable within 9 steps
// through leaves keeps the leaf, as the Fabric mod's LeafDistance (MAX = 10) does.
fn leaf_distance(level: &crate::game::GameLevel<'_>, pos: horizon_core::position::BlockPos, cancel: &mut bool) {
    use horizon_core::position::BlockPos;
    use horizon_world::level::LevelAccess;

    /// The distance at which a leaf decays (vanilla 7).
    const MAX: i32 = 10;
    const SIDES: [(i32, i32, i32); 6] = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)];

    // Names stand in for vanilla's #minecraft:logs and leaves tags; mod logs and leaves follow the same names.
    fn is_log(name: &str) -> bool {
        name.ends_with("_log") || name.ends_with("_wood") || name.ends_with("_stem") || name.ends_with("_hyphae")
    }
    fn is_leaves(name: &str) -> bool {
        name.ends_with("_leaves")
    }

    if *cancel {
        return;
    }
    let name_at = |p: BlockPos| level.native_state_block(LevelAccess::block_state(level, p)).unwrap_or("").to_owned();
    // Breadth first through leaves: step 1 is the leaf's neighbours. A log first met at step d means the leaf is
    // at distance d.
    let mut seen = std::collections::HashSet::new();
    seen.insert(pos);
    let mut frontier = vec![pos];
    for _step in 1..MAX {
        let mut next = Vec::new();
        for p in frontier {
            for (dx, dy, dz) in SIDES {
                let q = BlockPos::new(p.x + dx, p.y + dy, p.z + dz);
                if !seen.insert(q) {
                    continue;
                }
                let name = name_at(q);
                if is_log(&name) {
                    *cancel = true; // a log within 9 steps: the leaf stays
                    return;
                }
                if is_leaves(&name) {
                    next.push(q);
                }
            }
        }
        if next.is_empty() {
            return;
        }
        frontier = next;
    }
}
