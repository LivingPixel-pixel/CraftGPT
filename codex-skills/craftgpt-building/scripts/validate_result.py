#!/usr/bin/env python3
"""Validate a CraftGPT portable result without modifying either input file."""

from __future__ import annotations

import json
import re
import sys
import uuid
from pathlib import Path


SHA256 = re.compile(r"^[0-9a-f]{64}$")
INTEGER = re.compile(r"^(0|[1-9][0-9]*)$")
BLOCK_STATE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(?:\[[a-z0-9_=-]+(?:,[a-z0-9_=-]+)*\])?$")
DANGEROUS = {
    "anvil", "barrier", "bedrock", "cave_air", "chain_command_block", "command_block",
    "damaged_anvil", "chipped_anvil", "dragon_egg", "end_gateway", "end_portal",
    "end_portal_frame", "fire", "gravel", "jigsaw", "lava", "light", "moving_piston",
    "nether_portal", "piston_head", "repeating_command_block", "sand", "soul_fire",
    "spawner", "structure_block", "structure_void", "suspicious_gravel", "suspicious_sand",
    "test_block", "test_instance_block", "tnt", "trial_spawner", "vault", "void_air", "water"
}
PLAN_TEXT = ("title", "summary", "style", "orientation", "designRationale", "implementationBrief")
PLAN_LISTS = ("goals", "requiredFeatures", "preferredFeatures", "constraints", "avoid", "assumptions")


def fail(message: str) -> None:
    raise ValueError(message)


def load(path: str) -> dict:
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"{path}: root must be an object")
    return value


def nonempty(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate(request: dict, result: dict) -> None:
    if request.get("schemaVersion") != 1 or result.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    try:
        if str(uuid.UUID(request.get("requestId", ""))) != request.get("requestId"):
            fail("requestId is not canonical")
    except ValueError as error:
        fail(f"requestId is invalid: {error}")
    for field in ("requestId", "contextHash", "instructionHash"):
        if result.get(field) != request.get(field):
            fail(f"result {field} does not match request")
    for field in ("contextHash", "instructionHash"):
        if not SHA256.fullmatch(request.get(field, "")):
            fail(f"request {field} is invalid")

    area = request.get("areaContext")
    if not isinstance(area, dict):
        fail("areaContext is missing")
    width, height, depth = (area.get("width"), area.get("height"), area.get("depth"))
    if not all(isinstance(value, int) and value > 0 for value in (width, height, depth)):
        fail("area dimensions must be positive integers")
    limit = request.get("maximumOperations")
    if not isinstance(limit, int) or not 1 <= limit <= 10000:
        fail("maximumOperations must be between 1 and 10000")

    plan = result.get("plan")
    if not isinstance(plan, dict):
        fail("plan is missing")
    for field in PLAN_TEXT:
        if not nonempty(plan.get(field)):
            fail(f"plan.{field} must be non-empty")
    for field in PLAN_LISTS:
        values = plan.get(field)
        if not isinstance(values, list) or len(values) > 48 or not all(nonempty(value) for value in values):
            fail(f"plan.{field} must be a bounded string array")
    dimensions = plan.get("targetDimensions")
    if not isinstance(dimensions, dict):
        fail("plan.targetDimensions is missing")
    for field, maximum in (("width", width), ("height", height), ("depth", depth)):
        value = dimensions.get(field)
        if not isinstance(value, int) or not 1 <= value <= maximum:
            fail(f"plan.targetDimensions.{field} is out of bounds")
    estimate = plan.get("estimatedBlockChanges")
    if not isinstance(estimate, int) or not 0 <= estimate <= limit:
        fail("plan.estimatedBlockChanges is out of bounds")
    roles = plan.get("materialRoles")
    if not isinstance(roles, list) or len(roles) > 48:
        fail("plan.materialRoles must be a bounded array")
    for role in roles:
        if not isinstance(role, dict) or not nonempty(role.get("role")) or not nonempty(role.get("purpose")):
            fail("material role text is invalid")
        candidates = role.get("blockCandidates")
        if not isinstance(candidates, list) or not all(nonempty(value) for value in candidates):
            fail("material role candidates are invalid")

    build = result.get("build")
    if not isinstance(build, dict) or build.get("schemaVersion") != 1 or not nonempty(build.get("summary")):
        fail("build metadata is invalid")
    palette = build.get("palette")
    operations = build.get("operations")
    if not isinstance(palette, list) or not 1 <= len(palette) <= 256 or len(set(palette)) != len(palette):
        fail("build palette is invalid")
    for state in palette:
        if not isinstance(state, str) or not BLOCK_STATE.fullmatch(state):
            fail(f"invalid block state: {state!r}")
        block_id = state.split("[", 1)[0].split(":", 1)[1]
        if block_id in DANGEROUS or block_id.endswith("_concrete_powder") or "waterlogged=true" in state:
            fail(f"dangerous block state: {state}")
    if not isinstance(operations, list) or not 1 <= len(operations) <= limit:
        fail("build operations are missing or exceed the request limit")
    coordinates: set[tuple[int, int, int]] = set()
    for operation in operations:
        if not isinstance(operation, str):
            fail("operation must be a string")
        parts = operation.split(",")
        if len(parts) != 4 or not all(INTEGER.fullmatch(part) for part in parts):
            fail(f"invalid compact operation: {operation!r}")
        x, y, z, palette_index = map(int, parts)
        if x >= width or y >= height or z >= depth or palette_index >= len(palette):
            fail(f"out-of-bounds operation: {operation}")
        coordinate = (x, y, z)
        if coordinate in coordinates:
            fail(f"duplicate coordinate: {x},{y},{z}")
        coordinates.add(coordinate)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: validate_result.py request.craftgpt.json result.craftgpt.json", file=sys.stderr)
        return 2
    try:
        validate(load(sys.argv[1]), load(sys.argv[2]))
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"INVALID: {error}", file=sys.stderr)
        return 1
    print("VALID: CraftGPT request and result match")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
