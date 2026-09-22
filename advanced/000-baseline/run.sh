#!/usr/bin/env bash
# Lesson 000: the same build as lesson 001, with no agent and Go tracing off
exec "$(dirname "$0")/../scripts/start-lesson.sh" 001-auto --no-tracing
