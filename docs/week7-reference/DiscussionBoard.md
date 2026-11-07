# Discussion board

The notes below are retained as the original design discussion.

> What are we doing visually?
>
> Is the hive more of a house or on a tree?
>
> We can implement generics with the object picker that uses hitboxes to get a type and display information.
>
> The current worker bee is temporary and flowers crash the simulation when deleting themselves.

## Implemented decisions

- The existing tree and hanging-hive model remains the scene centerpiece. The supplied sprites draw workers and flowers. Queens, brood, wasps and exterminators are visible and inspectable.
- `ObjectPicker<T extends Inspectable>` implements generic selection using an independent collision box for each object.
- Worker behavior is a finite state machine with time-based movement, collection and defense.
- Workers reserve and release flowers. Depleted flowers are removed safely after updates.
- The model and renderer are separated so behavior can be tested without graphics.

See [CODE_WALKTHROUGH.md](CODE_WALKTHROUGH.md) for the implemented design and [VERIFICATION.md](VERIFICATION.md) for evidence.
