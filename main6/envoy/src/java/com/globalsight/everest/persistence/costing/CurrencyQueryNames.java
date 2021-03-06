/**
 *  Copyright 2009 Welocalize, Inc. 
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package com.globalsight.everest.persistence.costing;

/**
 * Specifies the names of all the named queries for Currency.
 */
public interface CurrencyQueryNames
{
    //
    // CONSTANTS REPRESENTING NAMES OF REGISTERED NAMED-QUERIES
    //
    /**
     * A named query to return all available currencies
     * <p>
     * Arguments: None.
     */
    public static String ALL_CURRENCIES = "getAllCurrencies";
 
    /**
     * A named query to return the currency associated with the code.
     * <p>
     * Arguments: THe 3 character iso code. (i.e. USD - for United States dollar)
     */                  
    public static String CURRENCY_BY_ISO_CODE = "getCurrencyByIsoCode";

    /**
     * A named query to return a Currency based on its id
     * <p>
     * Arguments: 1: Id.
     */
    public static String CURRENCY_BY_ID = "getCurrencyById"; 
}
