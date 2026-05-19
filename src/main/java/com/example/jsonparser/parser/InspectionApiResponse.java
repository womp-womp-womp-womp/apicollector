package com.example.jsonparser.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InspectionApiResponse {

    private List<InspectionRaw> data;

    public List<InspectionRaw> getData() {
        return data;
    }

    public void setData(List<InspectionRaw> data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InspectionRaw {

        private Long id;

        @JsonProperty("public_id")
        private Long publicId;

        private LocalDateTime date;

        @JsonProperty("stat_fails")
        private Integer statFails;

        @JsonProperty("stat_critical_fails")
        private Integer statCriticalFails;

        private PlaceRaw place;

        public Long getId() {
            return id;
        }

        public Long getPublicId() {
            return publicId;
        }

        public LocalDateTime getDate() {
            return date;
        }

        public Integer getStatFails() {
            return statFails;
        }

        public Integer getStatCriticalFails() {
            return statCriticalFails;
        }

        public PlaceRaw getPlace() {
            return place;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void setPublicId(Long publicId) {
            this.publicId = publicId;
        }

        public void setDate(LocalDateTime date) {
            this.date = date;
        }

        public void setStatFails(Integer statFails) {
            this.statFails = statFails;
        }

        public void setStatCriticalFails(Integer statCriticalFails) {
            this.statCriticalFails = statCriticalFails;
        }

        public void setPlace(PlaceRaw place) {
            this.place = place;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaceRaw {

        private String name;
        private String address;
        private List<GroupRaw> groups;

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }


        public List<GroupRaw> getGroups() {
            return groups;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setAddress(String address) {
            this.address = address;
        }


        public void setGroups(List<GroupRaw> groups) {
            this.groups = groups;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupRaw {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
