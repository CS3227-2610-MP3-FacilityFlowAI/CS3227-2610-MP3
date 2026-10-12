# FacilityFlow AI Domain

FacilityFlow AI coordinates reported facilities problems from report through
verified closure. These terms are business language, independent of web,
database, or LLM implementation.

## People and access

**Account**:
An authenticated identity with exactly one current role and an active state.
_Avoid_: User, profile

**Requester**:
The Account that owns a Request and can see only its own Requests.
_Avoid_: Reporter, customer

**Technician**:
The Account currently assigned to carry out maintenance work on a Request.
_Avoid_: Assignee, worker

**Facilities Manager**:
The Account that administers Accounts, triages Requests, controls assignment,
and reviews completed work.
_Avoid_: Admin, supervisor

## Maintenance work

**Request**:
A reported facilities problem owned by one Requester and governed by the
FacilityFlow lifecycle.
_Avoid_: Ticket, case, report

**Recording Actor**:
The authenticated Account that creates a Request, either its Requester owner or
a Facilities Manager acting on that Requester's behalf.
_Avoid_: Creator

**Reported Urgency**:
The Requester's assessment of impact: Low, Normal, High, or Emergency.
_Avoid_: Priority

**Manager Priority**:
The Facilities Manager's operational ranking: Low, Medium, High, or Critical.
_Avoid_: Urgency

**Assignment**:
The current responsibility of one active Technician for a Request.
_Avoid_: Recommendation

**Requester Update**:
A follow-up note visible to the owning Requester, assigned Technician, and
Facilities Manager.
_Avoid_: Work log

**Work Log**:
An append-only Technician record of work performed and whole minutes spent.
_Avoid_: Requester update

**Resolution Summary**:
The Technician's account of completed work submitted for Manager review.
_Avoid_: Closure

## Configuration and AI

**Category Catalogue**:
The Manager-controlled set of categories valid for Requests, including an
explicit replacement when a category is removed.
_Avoid_: Category list

**Skill Catalogue**:
The Manager-controlled set of maintenance skill tags that may be attached to
Technicians and recommended by triage.
_Avoid_: Agent skills

**AI Suggestion**:
A temporary, untrusted SoC LLM result that a human may accept, edit, or reject;
it is never a business action.
_Avoid_: Decision, assignment

**Technician Candidate**:
An active Technician ranked deterministically from approved skill tags and
current workload for a Manager to consider.
_Avoid_: AI assignment

**Audit Event**:
An immutable record of a significant authenticated business or security action.
_Avoid_: Operational log
