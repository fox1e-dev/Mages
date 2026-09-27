use std::collections::{BTreeSet, HashMap, HashSet};

use matrix_sdk::{
    Client,
    ruma::{
        OwnedRoomId,
        api::client::state::{
            get_state_event_for_key::v3 as state_event_for_key,
            get_state_events::v3 as state_events,
        },
        events::{
            StateEventType,
            image_pack::rooms::ImagePackRoomsEventContent,
            room::{
                MediaSource,
                image_pack::{ImagePackImage, PackUsage, RoomImagePackEventContent},
            },
            space::parent::SpaceParentEventContent,
        },
    },
};
use once_cell::sync::Lazy;
use serde::de::DeserializeOwned;
use tracing::warn;

use crate::{ImagePackImageEntry, ImagePackSummary};

static ROOM_IMAGE_PACK: Lazy<StateEventType> =
    Lazy::new(|| StateEventType::from("m.room.image_pack"));
static SPACE_PARENT: Lazy<StateEventType> = Lazy::new(|| StateEventType::from("m.space.parent"));

/// Spec v1.19 §42 asks implementations to "impose a reasonable limit on the
/// traversal depth" of the canonical space hierarchy.
const MAX_SPACE_DEPTH: usize = 8;

const PACK_ID_SEP: char = '\u{1f}';

/// Per-call cache of whole-room state. The protocol has no per-type state
/// endpoint, so every type has to be filtered out of a full dump; caching it
/// keeps a room that is read for both `m.space.parent` and `m.room.image_pack`
/// down to a single request.
struct StateCache<'a> {
    client: &'a Client,
    dumps: HashMap<OwnedRoomId, Vec<serde_json::Value>>,
}

impl<'a> StateCache<'a> {
    fn new(client: &'a Client) -> Self {
        Self { client, dumps: HashMap::new() }
    }

    async fn dump(&mut self, room: &OwnedRoomId) -> &[serde_json::Value] {
        if !self.dumps.contains_key(room) {
            let fetched = match self.client.send(state_events::Request::new(room.clone())).await
            {
                Ok(resp) => resp
                    .room_state
                    .iter()
                    .filter_map(|raw| serde_json::from_str(raw.json().get()).ok())
                    .collect(),
                Err(e) => {
                    warn!("image pack: state dump for {room} failed: {e}");
                    Vec::new()
                }
            };
            self.dumps.insert(room.clone(), fetched);
        }
        &self.dumps[room]
    }

    async fn of_type<T: DeserializeOwned>(
        &mut self,
        room: &OwnedRoomId,
        wanted: &StateEventType,
    ) -> Vec<(String, T)> {
        self.dump(room)
            .await
            .iter()
            .filter_map(|event| {
                if StateEventType::from(event.get("type")?.as_str()?) != *wanted {
                    return None;
                }
                let key = event.get("state_key")?.as_str()?.to_string();
                let content: T = serde_json::from_value(event.get("content")?.clone()).ok()?;
                Some((key, content))
            })
            .collect()
    }

    /// An empty `state_key` is a valid pack identifier, but it cannot be
    /// addressed through the per-key endpoint because it would collapse into a
    /// trailing path separator, so those come out of the cached dump instead.
    async fn by_key<T: DeserializeOwned>(
        &mut self,
        room: &OwnedRoomId,
        wanted: &StateEventType,
        state_key: &str,
    ) -> Vec<(String, T)> {
        if state_key.is_empty() {
            return self
                .of_type::<T>(room, wanted)
                .await
                .into_iter()
                .filter(|(k, _)| k.is_empty())
                .collect();
        }

        let req =
            state_event_for_key::Request::new(room.clone(), wanted.clone(), state_key.to_string());
        match self.client.send(req).await {
            // StateEventFormat::Content is the endpoint default, so the body is
            // the event content directly.
            Ok(resp) => match serde_json::from_str::<T>(resp.event_or_content.get()) {
                Ok(content) => vec![(state_key.to_string(), content)],
                Err(e) => {
                    warn!("image pack {room}/{state_key}: bad content: {e}");
                    Vec::new()
                }
            },
            Err(e) => {
                warn!("image pack {room}/{state_key}: state fetch failed: {e}");
                Vec::new()
            }
        }
    }
}

/// Resolve the image packs visible from `room_id`, in the spec's source-priority
/// order: `m.image_pack.rooms` account data, then the current room's own state,
/// then the canonical space hierarchy.
///
/// Packs reached through other rooms are restricted to the state keys the user
/// enabled in account data, per "Clients SHOULD present the images in a room's
/// packs only when the user is interacting in that room, unless that image pack
/// is enabled globally."
///
/// State is read over HTTP rather than from the local store: the client sends
/// no sync filter, so the store never holds these event types. Callers cache.
pub(crate) async fn list_image_packs(
    client: &Client,
    room_id: OwnedRoomId,
) -> Vec<ImagePackSummary> {
    let mut cache = StateCache::new(client);
    // A `None` key list means "every pack in this room"; a `Some` list is the
    // exact set of state keys the user enabled.
    let mut targets: Vec<(OwnedRoomId, Option<Vec<String>>)> = Vec::new();

    // Account data is user-writable, so a malformed blob must not take the
    // whole picker down; the other tiers still resolve.
    let subscribed = match client.account().account_data::<ImagePackRoomsEventContent>().await {
        Ok(Some(raw)) => raw.deserialize().ok().map(|c| c.rooms),
        Ok(None) => None,
        Err(e) => {
            warn!("image pack: account data unreadable: {e}");
            None
        }
    };

    // Subscribed packs are grouped per room so a room with several of them
    // costs one state fetch rather than one per pack.
    let mut subscribed_by_room: HashMap<OwnedRoomId, Vec<String>> = HashMap::new();
    for (target_room, packs) in subscribed.into_iter().flatten() {
        if target_room == room_id {
            continue;
        }
        // The user may have left a referenced room, in which case none of its
        // images are reachable.
        if client.get_room(&target_room).is_none() {
            continue;
        }
        subscribed_by_room
            .entry(target_room)
            .or_default()
            .extend(packs.into_keys());
    }

    // Sorted so the picker does not reshuffle packs between loads.
    let mut subscribed_rooms: Vec<OwnedRoomId> = subscribed_by_room.keys().cloned().collect();
    subscribed_rooms.sort();
    for room in subscribed_rooms {
        let keys = subscribed_by_room.remove(&room).unwrap_or_default();
        targets.push((room, Some(keys)));
    }

    targets.push((room_id.clone(), None));
    for space in canonical_space_ancestors(&mut cache, &room_id).await {
        targets.push((space, None));
    }

    let mut seen: HashSet<(OwnedRoomId, String)> = HashSet::new();
    let mut out: Vec<ImagePackSummary> = Vec::new();

    for (source_room, wanted_keys) in targets {
        let found: Vec<(String, RoomImagePackEventContent)> = match &wanted_keys {
            // A single non-empty key is cheaper to address directly than to
            // pull the room's whole state for.
            Some(keys) if keys.len() == 1 && !keys[0].is_empty() => {
                cache.by_key(&source_room, &ROOM_IMAGE_PACK, &keys[0]).await
            }
            Some(keys) => {
                let all = cache.of_type(&source_room, &ROOM_IMAGE_PACK).await;
                all.into_iter().filter(|(k, _)| keys.iter().any(|w| w == k)).collect()
            }
            None => cache.of_type(&source_room, &ROOM_IMAGE_PACK).await,
        };

        for (key, content) in found {
            if !seen.insert((source_room.clone(), key.clone())) {
                continue;
            }
            // A pack with no images is how a client deletes state, and the
            // room-name fallback for a missing display_name would otherwise
            // resurface it as an empty pack.
            if content.images.is_empty() {
                continue;
            }
            out.push(summary_from(source_room.to_string(), key, content));
        }
    }

    out
}

fn summary_from(
    source_room: String,
    state_key: String,
    content: RoomImagePackEventContent,
) -> ImagePackSummary {
    let mut images: Vec<ImagePackImageEntry> = content
        .images
        .into_iter()
        .map(|(shortcode, image)| entry_from(shortcode, image))
        .collect();
    images.sort_by(|a, b| a.shortcode.cmp(&b.shortcode));

    let pack = content.pack;
    ImagePackSummary {
        pack_id: format!("{source_room}{PACK_ID_SEP}{state_key}"),
        source_room,
        state_key,
        display_name: pack.display_name,
        avatar_url: pack.avatar_url.map(|m| m.to_string()),
        usage: usage_strings(&pack.usage),
        attribution: pack.attribution,
        images,
    }
}

fn usage_strings(usage: &BTreeSet<PackUsage>) -> Vec<String> {
    usage
        .iter()
        .filter_map(|u| serde_json::to_value(u).ok()?.as_str().map(str::to_string))
        .collect()
}

fn entry_from(shortcode: String, image: ImagePackImage) -> ImagePackImageEntry {
    // `ImageInfo::thumbnail_source` is a `MediaSource`, so it is either a plain
    // mxc or an encrypted file. Pack media is never encrypted, so only the plain
    // form can be fetched as a preview.
    let thumbnail_mxc_uri = image.info.as_ref().and_then(|info| match &info.thumbnail_source {
        Some(MediaSource::Plain(mxc)) => Some(mxc.to_string()),
        _ => None,
    });

    ImagePackImageEntry {
        shortcode,
        mxc_url: image.url.to_string(),
        body: image.body,
        info_json: image.info.map(|info| serde_json::to_string(&info).unwrap_or_default()),
        thumbnail_mxc_uri,
    }
}

/// Walk `m.space.parent` edges upwards, keeping only canonical parents the user
/// is joined to. Cycle-safe via `visited`, bounded by `MAX_SPACE_DEPTH`.
async fn canonical_space_ancestors(
    cache: &mut StateCache<'_>,
    room: &OwnedRoomId,
) -> Vec<OwnedRoomId> {
    let mut out = Vec::new();
    let mut visited: HashSet<OwnedRoomId> = HashSet::new();
    visited.insert(room.clone());

    let mut frontier: Vec<(OwnedRoomId, usize)> = vec![(room.clone(), 0)];
    while let Some((current, depth)) = frontier.pop() {
        if depth >= MAX_SPACE_DEPTH {
            continue;
        }
        let parents: Vec<(String, SpaceParentEventContent)> =
            cache.of_type(&current, &SPACE_PARENT).await;
        for (state_key, content) in parents {
            if !content.canonical || content.via.is_empty() {
                continue;
            }
            let Ok(space) = OwnedRoomId::try_from(state_key.as_str()) else { continue };
            if !visited.insert(space.clone()) {
                continue;
            }
            if cache.client.get_room(&space).is_none() {
                continue;
            }
            out.push(space.clone());
            frontier.push((space, depth + 1));
        }
    }

    out
}
