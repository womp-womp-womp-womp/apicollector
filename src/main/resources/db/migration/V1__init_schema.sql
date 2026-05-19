create table if not exists inspections (
    public_id bigint primary key,
    api_id bigint,
    inspection_date timestamp not null,
    violations_count integer not null default 0,
    critical_violations_count integer not null default 0,
    location_name text,
    location_address text,
    contractors_text text
);

create index if not exists idx_inspections_date_desc
    on inspections (inspection_date desc);

create table if not exists inspections_staging (
    public_id bigint primary key,
    api_id bigint,
    inspection_date timestamp,
    violations_count integer not null default 0,
    critical_violations_count integer not null default 0,
    location_name text,
    location_address text,
    contractors_text text
);

create index if not exists idx_inspections_staging_date_desc
    on inspections_staging (inspection_date desc);

create table if not exists contractors (
    contractor_name text primary key,
    violations_count integer not null default 0,
    critical_violations_count integer not null default 0,
    inspections_count integer not null default 0,
    total_score integer not null default 0,
    rating double precision not null default 0
);

create index if not exists idx_contractors_rating_asc
    on contractors (rating asc);

create index if not exists idx_contractors_total_score_asc
    on contractors (total_score asc);

create table if not exists app_users (
    username text primary key,
    password_hash text not null,
    salt text not null,
    role text not null,
    created_at timestamp not null default now()
);